param(
    [Parameter(Mandatory=$true)][ValidateSet('backend','frontend')][string]$Service,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{32}$')][string]$Token
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
Initialize-BusGoLocal ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')))
$job="Local\BusGo-$Token"
$workerLog=Join-Path $script:Local "logs/worker-$Token.log"
$secrets=[string[]]@()
try {
    [BusGoLocal.Native]::OwnJob($job)
    $config=Get-LauncherConfig $null
    $secrets=[string[]]@($config.Password,$config.RootPassword,$config.Jwt)
    $self=Get-Process -Id $PID
    $record=[pscustomobject]@{ Service=$Service; Token=$Token; Job=$job; Pid=$PID; StartTicks=$self.StartTime.ToUniversalTime().Ticks.ToString(); Executable=$self.Path; Root=$script:Repo; ConfigHash=(Get-ConfigFingerprint $config) }
    Write-LocalJson (Join-Path $script:Local "$Service.$Token.ready.json") $record
    $log=Join-Path $script:Local "logs/$Service-$Token.log"
    $environment=@{}
    if ($Service -eq 'backend') {
        $java=Get-RequiredCommand 'java.exe'; $maven=Get-RequiredCommand 'mvn.cmd'
        # Migration/seeding occurs only at java startup, after parent verified MySQL.
        $build=[BusGoLocal.Native]::Run($maven,[string[]]@('-B','-DskipTests','package'),(Join-Path $script:Repo 'backend'),$environment,$log,$secrets,600)
        if ($build.Code -ne 0) { throw 'Maven package failed. See backend log.' }
        $jarSource=Join-Path $script:Repo 'backend/target/busgo-0.0.1-SNAPSHOT.jar'
        $jar=Join-Path $script:Local 'busgo-local.jar'
        Assert-RegularPath $jar
        Copy-Item -LiteralPath $jarSource -Destination $jar -Force
        $environment=@{
            DB_URL="jdbc:mysql://127.0.0.1:$($config.Port)/$($config.Database)?connectionTimeZone=UTC";
            DB_USERNAME=$config.User; DB_PASSWORD=$config.Password; JWT_SECRET=$config.Jwt;
            SPRING_PROFILES_ACTIVE='dev,demo'; SERVER_PORT='8080'; MAIL_ENABLED='false';
            BUSGO_SYSTEM_ADMIN_BOOTSTRAP_ENABLED='false'
        }
        $run=[BusGoLocal.Native]::Run($java,[string[]]@('-Duser.timezone=UTC','-Dfile.encoding=UTF-8','-jar',$jar,'--server.address=127.0.0.1','--server.port=8080','--spring.profiles.active=dev,demo'),$script:Repo,$environment,$log,$secrets,2147483)
    } else {
        $node=Get-RequiredCommand 'node.exe'; $npm=Get-RequiredCommand 'npm.cmd'
        $environment=@{ NODE_ENV='development' }
        $frontend=Join-Path $script:Repo 'frontend'
        $lockPath=Join-Path $frontend 'package-lock.json'
        $packageHash=(Get-FileHash -LiteralPath $lockPath -Algorithm SHA256).Hash
        $stamp=Join-Path $script:Local 'frontend-lock.sha256'
        $vite=Join-Path $frontend 'node_modules/vite/bin/vite.js'
        if (-not (Test-Path $vite) -or -not (Test-Path $stamp) -or [IO.File]::ReadAllText($stamp) -ne $packageHash) {
            $install=[BusGoLocal.Native]::Run($npm,[string[]]@('ci','--include=dev','--no-audit','--no-fund'),$frontend,$environment,$log,$secrets,600)
            if ($install.Code -ne 0) { throw 'npm ci failed. See frontend log; lockfile was not rewritten.' }
            if ((Get-FileHash -LiteralPath $lockPath -Algorithm SHA256).Hash -ne $packageHash) { throw 'Lockfile changed during npm ci; refusing to continue.' }
            [IO.File]::WriteAllText($stamp,$packageHash)
        }
        $environment=@{ NODE_ENV='development'; API_PROXY_TARGET='http://127.0.0.1:8080'; VITE_API_BASE_URL='/api/v1' }
        $run=[BusGoLocal.Native]::Run($node,[string[]]@($vite,'--host','127.0.0.1','--port','5173','--strictPort','--mode','busgo-local'),$frontend,$environment,$log,$secrets,2147483)
    }
    throw "$Service process exited with code $($run.Code)."
} catch {
    $message=[BusGoLocal.Native]::Redact($_.Exception.Message,$secrets)
    [IO.File]::AppendAllText($workerLog,"$message`n",[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $script:Local "failed.$Token"),'Worker failed. Inspect service log.')
    exit 1
}
