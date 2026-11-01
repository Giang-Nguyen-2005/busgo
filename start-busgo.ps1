# Windows PowerShell 5.1 / PowerShell 7. No administrator shell is required.
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'scripts/local/Common.ps1')
$lock=$null
$started=New-Object System.Collections.Generic.List[object]
$startedDatabase=$false
$config=$null
$tools=$null
try {
    Initialize-BusGoLocal $PSScriptRoot
    $lock=Enter-LauncherLock
    Write-LauncherLog 'Start: checking prerequisites and local Docker.'
    Write-Host 'Checking Java 17, Maven, Node, npm and local Docker Desktop...'
    $tools=Get-StartPrerequisites
    $config=Get-LauncherConfig $tools -Create
    Assert-RegularPath (Join-Path $script:Local 'logs')
    [IO.Directory]::CreateDirectory((Join-Path $script:Local 'logs')) | Out-Null
    $container=Assert-DatabaseIdentity $tools $config
    foreach ($entry in @(@('backend',8080),@('frontend',5173))) {
        $record=Get-ServiceRecord $entry[0]
        if (Assert-WorkerIdentity $record) {
            if ($record.ConfigHash -ne (Get-ConfigFingerprint $config)) { throw 'Running worker configuration differs from saved config. Stop deliberately before changing configuration.' }
            $portOwners=@(Get-PortOwners $entry[1])
            foreach ($owner in $portOwners) { if (-not [BusGoLocal.Native]::InJob($record.Job,[int]$owner)) { throw "Port $($entry[1]) is occupied by another service." } }
        } else { Assert-FreePort $entry[1] }
    }
    if (-not $container -or -not $container.State.Running) { Assert-FreePort $config.Port }
    Write-Host 'Starting verified isolated MySQL (project busgo-local-demo, port 3308)...'
    if (-not $container -or -not $container.State.Running) {
        # Capture the fact that this invocation started the database for safe failure cleanup.
        $startedDatabase=$true
        $compose=Invoke-Docker $tools ((Get-ComposeArgs)+@('up','-d','--wait','--wait-timeout','180','mysql')) -AllowFailure -Timeout 240
        $safe=[BusGoLocal.Native]::Redact($compose.Output,[string[]]@($config.Password,$config.RootPassword,$config.Jwt))
        [IO.File]::AppendAllText((Join-Path $script:Local 'logs/docker.log'),$safe)
        if ($compose.Code -ne 0) { throw 'MySQL startup/healthcheck failed. Inspect docker.log (engine/port/credentials); existing data was preserved.' }
    }
    $container=Assert-DatabaseIdentity $tools $config
    if (-not $container -or -not $container.State.Running -or $container.State.Health.Status -ne 'healthy') { throw 'Verified MySQL is not healthy. Check credentials and docker.log; no backend was started.' }
    # Healthcheck uses the saved application user; also verify the actual selected schema.
    $query='MYSQL_PWD="$MYSQL_PASSWORD" mysql -h 127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -Nse "SELECT DATABASE()"'
    $database=Invoke-Docker $tools @('exec',$container.Id,'sh','-c',$query) -AllowFailure
    if ($database.Code -ne 0 -or $database.Output.Trim() -ne $config.Database) { throw 'Cannot authenticate/read the isolated launcher database. Backend was not started.' }
    Assert-MySqlReadyForMigrations $tools $container
    $backendResult=Ensure-OwnedService 'backend' 8080 $tools $config
    $backend=$backendResult.Record
    if ($backendResult.Created) { $started.Add($backend) }
    Write-Host 'Waiting for backend build, Flyway, demo seed and DB-backed trip search...'
    Wait-Backend $backend
    $frontendResult=Ensure-OwnedService 'frontend' 5173 $tools $config
    $frontend=$frontendResult.Record
    if ($frontendResult.Created) { $started.Add($frontend) }
    Write-Host 'Waiting for frontend dependencies, Vite and API proxy...'
    Wait-Frontend $frontend
    # Recheck ownership/readiness after both services have finished startup.
    Wait-Backend $backend -Timeout 15
    $container=Assert-DatabaseIdentity $tools $config
    if (-not $container.State.Running -or $container.State.Health.Status -ne 'healthy') { throw 'MySQL stopped during startup.' }
    Write-Host 'BusGo ready: http://localhost:5173'
    Write-LauncherLog 'Ready: verified MySQL, seed, backend, frontend and API proxy.'
    Write-Host 'Logs: .busgo-local/logs/ | Stop: .\stop-busgo.ps1 | Data and secrets persist.'
    exit 0
} catch {
    $message=$_.Exception.Message
    if ($config) { $message=[BusGoLocal.Native]::Redact($message,[string[]]@($config.Password,$config.RootPassword,$config.Jwt)) }
    Write-Host "BusGo start failed: $message" -ForegroundColor Red
    if ($lock) { try { Write-LauncherLog "Start failed: $message" } catch { } }
    # Never stop a worker which predated this invocation.
    for ($i=$started.Count-1; $i -ge 0; $i--) {
        try { Stop-OwnedService $started[$i] } catch { Write-Warning 'Could not verify/stop a newly created worker. Nothing else was killed.' }
    }
    if ($startedDatabase -and $config -and $tools) {
        try {
            $owned=Assert-DatabaseIdentity $tools $config
            if ($owned -and $owned.State.Running) { $null=Invoke-Docker $tools @('stop','--time','30',$owned.Id) }
        } catch { Write-Warning 'Database cleanup was not verifiable; retained resources and all data.' }
    }
    exit 1
} finally { if ($lock) { $lock.Dispose() } }
