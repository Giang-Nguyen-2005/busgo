Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Initialize-BusGoLocal {
    param([string]$Root)
    if ([Environment]::OSVersion.Platform -ne 'Win32NT' -or $PSVersionTable.PSVersion -lt [version]'5.1') {
        throw 'BusGo launcher requires Windows PowerShell 5.1 or PowerShell 7 on Windows.'
    }
    $script:Repo = [IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    $script:Local = Join-Path $script:Repo '.busgo-local'
    if (-not ('BusGoLocal.Native' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'Native.cs') }
}

function Assert-RegularPath {
    param([string]$Path)
    if ((Test-Path -LiteralPath $Path) -and ((Get-Item -Force -LiteralPath $Path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Launcher refuses a symlink/junction at $Path."
    }
}

function Enter-LauncherLock {
    Assert-RegularPath $script:Local
    $existing=Test-Path -LiteralPath $script:Local
    if (-not $existing) {
        [IO.Directory]::CreateDirectory($script:Local) | Out-Null
        # Secrets inherit access only for this Windows user and SYSTEM.
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User, [Security.Principal.SecurityIdentifier]'S-1-5-18')) {
            $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $acl.AddAccessRule($rule)
        }
        Set-Acl -LiteralPath $script:Local -AclObject $acl
    }
    $savedAcl=Get-Acl -LiteralPath $script:Local
    $allowedSids=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18')
    if (-not $savedAcl.AreAccessRulesProtected) { throw 'Existing .busgo-local permissions are not private. Restrict access to this Windows user and SYSTEM before retrying.' }
    foreach ($access in $savedAcl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])) {
        if ($access.AccessControlType -eq 'Allow' -and $allowedSids -notcontains $access.IdentityReference.Value) { throw 'Existing .busgo-local grants access to another identity; refusing to read secrets.' }
    }
    $lockPath = Join-Path $script:Local 'launcher.lock'
    Assert-RegularPath $lockPath
    try { return [IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Another start/stop is running (or the launcher lock cannot be opened). Try again after it finishes.' }
}

function Write-LocalJson {
    param([string]$Path, $Value)
    Assert-RegularPath $Path
    $temp = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
    [IO.File]::WriteAllText($temp, ($Value | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temp -Destination $Path -Force
}

function Read-LocalJson {
    param([string]$Path)
    Assert-RegularPath $Path
    try { return [IO.File]::ReadAllText($Path) | ConvertFrom-Json }
    catch { throw "Invalid launcher JSON: $Path. Preserve it and investigate; do not reset data." }
}

function Write-LauncherLog {
    param([string]$Message)
    $directory=Join-Path $script:Local 'logs'
    Assert-RegularPath $directory
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    $path=Join-Path $directory 'launcher.log'
    Assert-RegularPath $path
    [IO.File]::AppendAllText($path,"$([DateTime]::UtcNow.ToString('o')) $Message`n",[Text.UTF8Encoding]::new($false))
}

function Get-TextHash {
    param([string]$Text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function New-LocalSecret {
    param([switch]$Base64)
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    if ($Base64) { return [Convert]::ToBase64String($bytes) }
    return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
}

function Invoke-LocalCommand {
    param([string]$File, [string[]]$Arguments, [hashtable]$Environment = @{}, [int]$Timeout = 60)
    return [BusGoLocal.Native]::Run($File, $Arguments, $script:Repo, $Environment, '', [string[]]@(), $Timeout)
}

function Get-RequiredCommand {
    param([string]$Name)
    $found = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $found) { throw "Missing $Name. Install the prerequisite, configure PATH, then open a new terminal." }
    return $found.Source
}

function Assert-VersionOutput {
    param($Result, [string]$Pattern, [string]$Message)
    if ($Result.Code -ne 0 -or $Result.Output -notmatch $Pattern) { throw $Message }
}

function Get-JdkCandidates {
    $candidates=New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    foreach ($java in @(Get-Command java.exe -CommandType Application -All -ErrorAction SilentlyContinue)) {
        $candidates.Add((Split-Path (Split-Path $java.Source -Parent) -Parent))
    }
    $roots=New-Object System.Collections.Generic.List[string]
    $roots.Add((Join-Path $script:Repo '.tools/jdk17'))
    foreach ($base in @($env:ProgramFiles,${env:ProgramFiles(x86)},$env:LOCALAPPDATA)) {
        if (-not $base) { continue }
        foreach ($vendor in @('Java','Microsoft','Eclipse Adoptium','AdoptOpenJDK','Amazon Corretto','BellSoft','Semeru','Programs/Eclipse Adoptium','Programs/Microsoft','Programs/Java')) {
            $roots.Add((Join-Path $base $vendor))
        }
    }
    foreach ($root in $roots) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            $candidates.Add($root)
            foreach ($directory in @(Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | Sort-Object Name)) { $candidates.Add($directory.FullName) }
        }
    }
    foreach ($registryRoot in @('HKLM:/SOFTWARE/JavaSoft/JDK','HKLM:/SOFTWARE/JavaSoft/Java Development Kit','HKLM:/SOFTWARE/WOW6432Node/JavaSoft/JDK')) {
        if (Test-Path $registryRoot) {
            foreach ($key in @(Get-ChildItem $registryRoot -ErrorAction SilentlyContinue)) {
                $properties=Get-ItemProperty $key.PSPath -ErrorAction SilentlyContinue
                if ($properties -and $properties.PSObject.Properties['JavaHome']) { $candidates.Add($properties.JavaHome) }
            }
        }
    }
    $seen=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($candidate in $candidates) { if ($candidate -and $seen.Add($candidate)) { $candidate } }
}

function Find-Jdk17 {
    foreach ($candidate in @(Get-JdkCandidates)) {
        $java=Join-Path $candidate 'bin/java.exe'; $javac=Join-Path $candidate 'bin/javac.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or -not (Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
        $environment=@{JAVA_HOME=$candidate;PATH=((Join-Path $candidate 'bin')+';'+$env:Path)}
        try {
            $javaVersion=Invoke-LocalCommand $java @('-version') -Environment $environment -Timeout 15
            $compilerVersion=Invoke-LocalCommand $javac @('-version') -Environment $environment -Timeout 15
            if ($javaVersion.Code -eq 0 -and $javaVersion.Output -match 'version "17\.' -and $compilerVersion.Code -eq 0 -and $compilerVersion.Output -match '^\s*javac 17\.') {
                return [pscustomobject]@{Home=$candidate;Java=$java;Environment=$environment}
            }
        } catch { } # Invalid candidate: try the next installed/local JDK, never Java 8.
    }
    throw 'JDK 17 was not found. Install a JDK 17 and set JAVA_HOME to its root (or put its bin on PATH). A prepared .tools/jdk17 JDK is also supported. No software was downloaded or system variables changed.'
}

function Get-LocalDocker {
    $docker = Get-RequiredCommand 'docker.exe'
    $contextResult = Invoke-LocalCommand $docker @('context','show')
    if ($contextResult.Code -ne 0) { throw 'Cannot read Docker context. Open Docker Desktop and check its setup.' }
    $contextName = $contextResult.Output.Trim()
    $inspection = Invoke-LocalCommand $docker @('context','inspect',$contextName)
    if ($inspection.Code -ne 0) { throw 'Cannot inspect Docker context.' }
    try { $endpoint = @($inspection.Output | ConvertFrom-Json)[0].Endpoints.docker.Host }
    catch { throw 'Cannot verify Docker endpoint.' }
    if ($endpoint -notmatch '^npipe:////\./pipe/(docker_engine|dockerDesktopLinuxEngine)$') {
        throw 'Only a local Docker Desktop named-pipe endpoint is allowed. Remote TCP/SSH Docker is refused.'
    }
    # Explicit context on every command prevents inherited DOCKER_HOST from redirecting it.
    $info = Invoke-LocalCommand $docker @('--context',$contextName,'info','--format','{{.OSType}}')
    Assert-VersionOutput $info '^\s*linux\s*$' 'Docker Linux engine is not ready. Open Docker Desktop, select Linux containers, wait, and retry.'
    $engine = Invoke-LocalCommand $docker @('--context',$contextName,'info','--format','{{.ID}}')
    Assert-VersionOutput $engine '^\s*[a-zA-Z0-9:-]+\s*$' 'Cannot verify the local Docker engine ID.'
    $compose = Invoke-LocalCommand $docker @('--context',$contextName,'compose','version','--short')
    if ($compose.Code -ne 0 -or $compose.Output.Trim() -notmatch '^v?(\d+)\.\d+' -or [int]$Matches[1] -lt 2) { throw 'Docker Compose v2 or newer is required.' }
    $help = Invoke-LocalCommand $docker @('--context',$contextName,'compose','up','--help')
    Assert-VersionOutput $help '--wait-timeout' 'Update Docker Compose v2 manually: up --wait-timeout support is required.'
    return @{ Docker=$docker; Context=$contextName; EngineId=$engine.Output.Trim() }
}

function Get-StartPrerequisites {
    foreach ($relative in @('backend/pom.xml','frontend/package.json','frontend/package-lock.json','frontend/vite.config.ts','docker-compose.yml','scripts/local/Worker.ps1','scripts/local/mysql-demo.yml')) {
        if (-not (Test-Path -LiteralPath (Join-Path $script:Repo $relative) -PathType Leaf)) { throw "Missing repository file: $relative" }
    }
    $jdk=Find-Jdk17
    $java = $jdk.Java; $maven = Get-RequiredCommand 'mvn.cmd'
    $node = Get-RequiredCommand 'node.exe'; $npm = Get-RequiredCommand 'npm.cmd'
    $mavenResult=Invoke-LocalCommand $maven @('-version') -Environment $jdk.Environment
    Assert-VersionOutput $mavenResult 'Java version: 17\.' 'Maven must actually use Java 17. Check mvn -version and JAVA_HOME in this terminal.'
    if ($mavenResult.Output -notmatch 'Apache Maven (\d+\.\d+\.\d+)' -or [version]$Matches[1] -lt [version]'3.6.3') { throw 'Maven >=3.6.3 is required.' }
    $nodeResult = Invoke-LocalCommand $node @('--version')
    if ($nodeResult.Code -ne 0 -or $nodeResult.Output.Trim() -notmatch '^v(\d+)\.(\d+)\.(\d+)$') { throw 'Cannot execute Node.js.' }
    if ([version]$nodeResult.Output.Trim().TrimStart('v') -lt [version]'22.12.0') { throw 'Node.js >=22.12.0 is required.' }
    Assert-VersionOutput (Invoke-LocalCommand $npm @('--version')) '^\s*\d+\.\d+' 'npm is not working. Repair the Node.js/npm installation.'
    $tools = Get-LocalDocker
    $tools.Java=$java; $tools.Maven=$maven; $tools.Node=$node; $tools.Npm=$npm
    $tools.JavaHome=$jdk.Home; $tools.ChildPath=$jdk.Environment.PATH
    return $tools
}

function Invoke-Docker {
    param($Tools, [string[]]$Arguments, [switch]$AllowFailure, [int]$Timeout = 90)
    $result = Invoke-LocalCommand $Tools.Docker (@('--context',$Tools.Context) + $Arguments) -Timeout $Timeout
    if ($result.Code -ne 0 -and -not $AllowFailure) { throw 'Docker command failed. Check Docker Desktop, context, port and launcher logs; no data was deleted.' }
    return $result
}

function Get-ConfigFingerprint {
    param($Config)
    return Get-TextHash (@($Config.Id,$Config.Root,$Config.Context,$Config.EngineId,$Config.Project,$Config.Database,$Config.User,$Config.Port,$Config.Password,$Config.RootPassword,$Config.Jwt) -join "`n")
}

function Get-ComposeText {
    param($Config)
    return "MYSQL_DATABASE=$($Config.Database)`nMYSQL_USER=$($Config.User)`nMYSQL_PASSWORD=$($Config.Password)`nMYSQL_ROOT_PASSWORD=$($Config.RootPassword)`nMYSQL_PORT=$($Config.Port)`n"
}

function Get-OverrideText {
    param($Config)
    $hash = Get-ConfigFingerprint $Config
    return "services:`n  mysql:`n    labels:`n      io.busgo.launcher.instance: '$($Config.Id)'`n      io.busgo.launcher.config: '$hash'`nvolumes:`n  mysql_data:`n    labels:`n      io.busgo.launcher.instance: '$($Config.Id)'`n      io.busgo.launcher.config: '$hash'`n"
}

function Get-LauncherConfig {
    param($Tools, [switch]$Create)
    $configPath = Join-Path $script:Local 'config.json'
    $envPath = Join-Path $script:Local 'compose.env'
    $overridePath = Join-Path $script:Local 'compose.override.yml'
    if (-not (Test-Path -LiteralPath $configPath)) {
        if (-not $Create) { throw 'No launcher configuration. Nothing was stopped.' }
        $volumes = Invoke-Docker $Tools @('volume','ls','--format','{{.Name}}')
        $containers = Invoke-Docker $Tools @('ps','-a','--filter','label=com.docker.compose.project=busgo-local-demo','--format','{{.ID}}')
        if ($volumes.Output -match '(?m)^busgo-local-demo_mysql_data\s*$' -or $containers.Output.Trim() -or (Test-Path $envPath) -or (Test-Path $overridePath)) {
            throw 'Launcher configuration is missing but existing launcher resources/files were found. Cannot recover credentials or claim ownership automatically.'
        }
        $config = [pscustomobject]@{ Version=1; Id=[guid]::NewGuid().ToString('N'); Root=$script:Repo; Context=$Tools.Context; EngineId=$Tools.EngineId; Project='busgo-local-demo'; Database='busgo_launcher_demo'; User='busgo'; Port=3308; Password=(New-LocalSecret); RootPassword=(New-LocalSecret); Jwt=(New-LocalSecret -Base64) }
        foreach ($entry in @(@($envPath,(Get-ComposeText $config)),@($overridePath,(Get-OverrideText $config)),@($configPath,($config | ConvertTo-Json)))) {
            $stream = [IO.File]::Open($entry[0], 'CreateNew', 'Write', 'None')
            try { $bytes=[Text.Encoding]::UTF8.GetBytes($entry[1]); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
        }
    }
    $config = Read-LocalJson $configPath
    try {
        if ($config.Version -ne 1 -or $config.Root -ne $script:Repo -or -not $config.Context -or -not $config.EngineId -or $config.Project -ne 'busgo-local-demo' -or $config.Database -ne 'busgo_launcher_demo' -or $config.User -ne 'busgo' -or $config.Port -ne 3308 -or $config.Id -notmatch '^[a-f0-9]{32}$' -or $config.Password -notmatch '^[a-f0-9]{64}$' -or $config.RootPassword -notmatch '^[a-f0-9]{64}$' -or [Convert]::FromBase64String($config.Jwt).Length -lt 32) { throw 'Invalid values' }
        foreach ($path in @($envPath,$overridePath)) { Assert-RegularPath $path }
        if ([IO.File]::ReadAllText($envPath) -cne (Get-ComposeText $config) -or [IO.File]::ReadAllText($overridePath) -cne (Get-OverrideText $config)) { throw 'Inconsistent files' }
    } catch { throw 'Launcher configuration is invalid/inconsistent. Preserve config and volume; changing env passwords does not update initialized MySQL accounts.' }
    if ($Tools -and ($Tools.Context -ne $config.Context -or $Tools.EngineId -ne $config.EngineId)) { throw 'Docker context/engine differs from saved config. Refusing to create or claim a different database.' }
    return $config
}

function Get-ComposeArgs {
    return @('compose','--project-name','busgo-local-demo','--project-directory',$script:Repo,'--env-file',(Join-Path $script:Local 'compose.env'),'-f',(Join-Path $script:Repo 'docker-compose.yml'),'-f',(Join-Path $script:Local 'compose.override.yml'),'-f',(Join-Path $script:Repo 'scripts/local/mysql-demo.yml'))
}

function Assert-MySqlReadyForMigrations {
    param($Tools,$Container)
    $probe='MYSQL_PWD="$MYSQL_PASSWORD" mysql -h 127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -Nse "SELECT @@log_bin_trust_function_creators; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=''flyway_schema_history''"'
    $result=Invoke-Docker $Tools @('exec',$Container.Id,'sh','-c',$probe) -AllowFailure
    if ($result.Code -ne 0) { throw 'Cannot verify MySQL migration prerequisites. Backend was not started.' }
    $values=@($result.Output.Trim() -split '\r?\n')
    if ($values.Count -ne 2 -or $values[0] -ne '1') { throw 'Launcher MySQL trigger support is not enabled. Stop/start the owned launcher to apply its MySQL override; do not change the existing 3307 database.' }
    if ($values[1] -eq '1') {
        $query='MYSQL_PWD="$MYSQL_PASSWORD" mysql -h 127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -Nse "SELECT version FROM flyway_schema_history WHERE success=0"'
        $failed=Invoke-Docker $Tools @('exec',$Container.Id,'sh','-c',$query) -AllowFailure
        if ($failed.Code -ne 0) { throw 'Cannot validate Flyway history before backend startup.' }
        if ($failed.Output.Trim()) {
            $versions=$failed.Output.Trim()
            if ($versions -notmatch '^[0-9.\s]+$') { $versions='unknown' }
            throw "Failed Flyway migration(s): $versions. Manual recovery must be reviewed explicitly; launcher will not repair/reset the database. Backend was not started."
        }
    } elseif ($values[1] -ne '0') { throw 'Unexpected Flyway history inspection result.' }
}

function Assert-DatabaseIdentity {
    param($Tools,$Config)
    if ($Tools.Context -ne $Config.Context -or $Tools.EngineId -ne $Config.EngineId) { throw 'Docker context/engine ownership mismatch. No database was touched.' }
    $hash = Get-ConfigFingerprint $Config
    $volume = Invoke-Docker $Tools @('volume','inspect','busgo-local-demo_mysql_data') -AllowFailure
    if ($volume.Code -eq 0) {
        $v = @($volume.Output | ConvertFrom-Json)[0]
        if ($v.Labels.'com.docker.compose.project' -ne $Config.Project -or $v.Labels.'io.busgo.launcher.instance' -ne $Config.Id -or $v.Labels.'io.busgo.launcher.config' -ne $hash) { throw 'MySQL volume ownership/config mismatch. Refusing to use or modify it.' }
    } elseif ($volume.Output -notmatch 'No such volume') { throw 'Cannot verify launcher volume. Docker inspection failed.' }
    $listed = Invoke-Docker $Tools @('ps','-a','--filter',"label=com.docker.compose.project=$($Config.Project)",'--format','{{.ID}}')
    $ids = @($listed.Output -split '\r?\n' | Where-Object { $_.Trim() })
    if ($ids.Count -gt 1) { throw 'Unexpected extra containers in launcher project. Refusing to continue.' }
    if ($ids.Count -eq 0) { return $null }
    $inspection = Invoke-Docker $Tools @('inspect',$ids[0])
    $c = @($inspection.Output | ConvertFrom-Json)[0]
    $mounts = @($c.Mounts | Where-Object { $_.Destination -eq '/var/lib/mysql' })
    $bindings = @($c.HostConfig.PortBindings.'3306/tcp')
    if ($volume.Code -ne 0 -or $c.Config.Image -ne 'mysql:8.4' -or $c.Config.Labels.'com.docker.compose.project' -ne $Config.Project -or $c.Config.Labels.'com.docker.compose.service' -ne 'mysql' -or $c.Config.Labels.'io.busgo.launcher.instance' -ne $Config.Id -or $c.Config.Labels.'io.busgo.launcher.config' -ne $hash -or $mounts.Count -ne 1 -or $mounts[0].Name -ne 'busgo-local-demo_mysql_data' -or $bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '3308') { throw 'MySQL container identity/mount/port mismatch. Refusing to use or stop it.' }
    foreach ($expected in @("MYSQL_DATABASE=$($Config.Database)","MYSQL_USER=$($Config.User)","MYSQL_PASSWORD=$($Config.Password)","MYSQL_ROOT_PASSWORD=$($Config.RootPassword)")) {
        if ($c.Config.Env -cnotcontains $expected) { throw 'Container credentials differ from saved launcher config; no password or data was changed.' }
    }
    return $c
}

function Get-PortOwners {
    param([int]$Port)
    # Enumerating all listeners avoids treating access-denied errors as a free port.
    return @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -eq $Port } | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Assert-FreePort {
    param([int]$Port)
    if (@(Get-PortOwners $Port).Count) { throw "Port $Port is occupied by an existing service. Nothing was killed; stop it deliberately or use another machine." }
}

function Get-ServiceRecord {
    param([string]$Service)
    $path = Join-Path $script:Local "$Service.json"
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    return Read-LocalJson $path
}

function Assert-WorkerIdentity {
    param($Record)
    if (-not $Record) { return $false }
    if ($Record.Token -notmatch '^[a-f0-9]{32}$' -or $Record.Service -notin @('backend','frontend') -or $Record.Root -ne $script:Repo -or $Record.Job -ne "Local\BusGo-$($Record.Token)") { throw 'Invalid worker state; no process was touched.' }
    $process = Get-Process -Id $Record.Pid -ErrorAction SilentlyContinue
    if (-not $process) { return $false }
    if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $Record.StartTicks -or $process.Path -ne $Record.Executable) { throw 'Saved worker PID has been reused or executable changed. No process was touched.' }
    $info = Get-CimInstance Win32_Process -Filter "ProcessId=$($Record.Pid)" -ErrorAction Stop
    $workerPath = Join-Path $script:Repo 'scripts/local/Worker.ps1'
    if (-not $info -or $info.CommandLine -notlike "*$workerPath*" -or $info.CommandLine -notlike "*$($Record.Token)*" -or -not [BusGoLocal.Native]::InJob($Record.Job,[int]$Record.Pid)) { throw 'Cannot verify worker command line and Windows job. No process was touched.' }
    return $true
}

function Assert-ServicePort {
    param($Record,[int]$Port)
    if (-not (Assert-WorkerIdentity $Record)) { throw "Managed $($Record.Service) exited. Inspect its log." }
    $owners = @(Get-PortOwners $Port)
    if (-not $owners.Count) { return $false }
    foreach ($owner in $owners) { if (-not [BusGoLocal.Native]::InJob($Record.Job,[int]$owner)) { throw "Port $Port belongs to another process; refusing to use it." } }
    return $true
}

function Stop-OwnedService {
    param($Record)
    if (-not (Assert-WorkerIdentity $Record)) { return }
    [BusGoLocal.Native]::StopJob($Record.Job,[int]$Record.Pid)
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    while ((Get-Process -Id $Record.Pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 200 }
    if (Get-Process -Id $Record.Pid -ErrorAction SilentlyContinue) { throw 'Verified worker did not stop within timeout.' }
}

function Start-OwnedService {
    param([string]$Service,$Tools,$Config)
    $token=[guid]::NewGuid().ToString('N')
    $workerPath=Join-Path $script:Repo 'scripts/local/Worker.ps1'
    $hostExe=(Get-Process -Id $PID).Path
    # Only paths, role and a non-secret run token are on the command line.
    # Execution policy is scoped to this hidden child only; machine/user policy is untouched.
    $process=[BusGoLocal.Native]::SpawnWithEnvironment($hostExe,[string[]]@('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',$workerPath,'-Service',$Service,'-Token',$token),$script:Repo,@{JAVA_HOME=$Tools.JavaHome;PATH=$Tools.ChildPath})
    $readyPath=Join-Path $script:Local "$Service.$token.ready.json"
    $pending=[pscustomobject]@{Service=$Service;Token=$token;Job="Local\BusGo-$token";Pid=$process.Id;StartTicks=$process.StartTime.ToUniversalTime().Ticks.ToString();Executable=$hostExe;Root=$script:Repo;ConfigHash=(Get-ConfigFingerprint $Config)}
    # Record even an initializing worker, so a timed-out handshake cannot cause
    # the next invocation to blindly spawn a duplicate.
    Write-LocalJson (Join-Path $script:Local "$Service.json") $pending
    try {
        $deadline=[DateTime]::UtcNow.AddSeconds(60)
        while (-not (Test-Path -LiteralPath $readyPath)) {
            if ($process.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw "Cannot initialize $Service worker. See .busgo-local/logs/worker-$token.log; no unrelated process was stopped." }
            Start-Sleep -Milliseconds 200
        }
        $record=Read-LocalJson $readyPath
        if ($record.Pid -ne $process.Id -or $record.Token -ne $token -or -not (Assert-WorkerIdentity $record) -or $record.ConfigHash -ne (Get-ConfigFingerprint $Config)) { throw 'New worker handshake failed.' }
        Write-LocalJson (Join-Path $script:Local "$Service.json") $record
        return $record
    } catch {
        try { Stop-OwnedService $pending } catch { Write-Warning 'Initializing worker could not be verified; retained its state and did not kill blindly.' }
        throw
    }
}

function Ensure-OwnedService {
    param([string]$Service,[int]$Port,$Tools,$Config)
    $record=Get-ServiceRecord $Service
    if (Assert-WorkerIdentity $record) {
        if ($record.ConfigHash -ne (Get-ConfigFingerprint $Config)) { throw 'Existing worker config differs. Nothing was restarted.' }
        return [pscustomobject]@{ Record=$record; Created=$false }
    }
    Assert-FreePort $Port
    return [pscustomobject]@{ Record=(Start-OwnedService $Service $Tools $Config); Created=$true }
}

function Get-JsonResponse {
    param([string]$Url)
    # RFC 8259 JSON is UTF-8 even when Content-Type omits charset. Windows
    # PowerShell 5.1's web cmdlets otherwise decode Spring's JSON as Latin-1.
    $request=[Net.WebRequest]::Create($Url)
    $request.Method='GET'; $request.Accept='application/json'; $request.Proxy=$null
    $request.Timeout=4000; $request.ReadWriteTimeout=4000; $request.AllowAutoRedirect=$false
    $response=$null; $reader=$null
    try {
        $response=$request.GetResponse()
        if ([int]$response.StatusCode -ne 200 -or $response.ContentType -notmatch '^application/json') { throw 'Unexpected JSON readiness response.' }
        $reader=[IO.StreamReader]::new($response.GetResponseStream(),[Text.Encoding]::UTF8)
        return ($reader.ReadToEnd() | ConvertFrom-Json)
    } finally {
        if ($reader) { $reader.Dispose() }
        if ($response) { $response.Dispose() }
    }
}

function Wait-Backend {
    param($Record,[int]$Timeout=900)
    $deadline=[DateTime]::UtcNow.AddSeconds($Timeout)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Assert-WorkerIdentity $Record)) { throw 'Backend worker exited (Maven/Flyway/seed failure). Inspect backend log; database was preserved.' }
        $failure=Join-Path $script:Local "failed.$($Record.Token)"
        if (Test-Path $failure) { throw 'Backend build/start failed. Inspect backend log; database was preserved.' }
        if (Assert-ServicePort $Record 8080) {
            try {
                $log=Join-Path $script:Local "logs/backend-$($Record.Token).log"
                if ((Test-Path $log) -and (Select-String -LiteralPath $log -SimpleMatch 'BusGo demo seed finished:' -Quiet)) {
                    $health=Get-JsonResponse 'http://127.0.0.1:8080/api/v1/health'
                    $locations=Get-JsonResponse 'http://127.0.0.1:8080/api/v1/locations'
                    # Inspect the seeded catalogue, not guessed database IDs or dates.
                    $pickup=@($locations.data | Where-Object { $_.name -eq ([string][char]0x42 + [char]0x1ebf + 'n xe TP. H' + [char]0x1ed3 + ' Ch' + [char]0x00ed + ' Minh') })
                    $dropoff=@($locations.data | Where-Object { $_.name -eq ('B' + [char]0x1ebf + 'n xe ' + [char]0x0110 + [char]0x00e0 + ' L' + [char]0x1ea1 + 't') })
                    if ($health.data.status -eq 'UP' -and $pickup.Count -eq 1 -and $dropoff.Count -eq 1) {
                        $zone=[TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
                        $day=[TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow,$zone).AddDays(1).ToString('yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
                        $search=Get-JsonResponse "http://127.0.0.1:8080/api/v1/trips/search?pickupLocationId=$($pickup[0].id)&dropoffLocationId=$($dropoff[0].id)&departureDate=$day"
                        if ($search.data -and @($search.data).Count -gt 0) { return }
                    }
                }
            } catch { } # Transient HTTP/runner readiness; ownership errors above are never swallowed.
        }
        Start-Sleep -Milliseconds 700
    }
    throw 'Backend readiness timed out: need seed completion, health, DB locations and a future demo trip. Inspect backend log; no data was reset.'
}

function Wait-Frontend {
    param($Record,[int]$Timeout=900)
    $deadline=[DateTime]::UtcNow.AddSeconds($Timeout)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Assert-WorkerIdentity $Record)) { throw 'Frontend worker exited. Inspect frontend log.' }
        if (Assert-ServicePort $Record 5173) {
            try {
                $page=Invoke-WebRequest 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 4 -MaximumRedirection 0
                $health=Get-JsonResponse 'http://127.0.0.1:5173/api/v1/health'
                $locations=Get-JsonResponse 'http://127.0.0.1:5173/api/v1/locations'
                if ($page.StatusCode -eq 200 -and $page.Content -match '/src/main.tsx' -and $health.data.status -eq 'UP' -and $locations.data -and @($locations.data).Count -gt 0) { return }
            } catch { }
        }
        Start-Sleep -Milliseconds 700
    }
    throw 'Frontend/app/API proxy readiness timed out. Inspect frontend log.'
}
