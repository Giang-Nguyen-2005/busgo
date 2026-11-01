# No Docker, database, application server or external test framework is required.
[CmdletBinding()]
param([switch]$SkipProcessTest)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $repoRoot 'scripts/local/Common.ps1')
Initialize-BusGoLocal $repoRoot
$testRoot=Join-Path $repoRoot ('.tools/launcher-tests/'+[guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($testRoot) | Out-Null
$script:passed=0; $script:failed=0; $script:mocks=@{}
$script:fakeTools=@{Context='desktop-linux';EngineId='isolated-test-engine'}
function Assert { param([bool]$Condition,[string]$Message='Assertion failed'); if (-not $Condition) { throw $Message } }
function Throws { param([scriptblock]$Action,[string]$Pattern='.')
    $caught=$false
    try { & $Action | Out-Null } catch { $caught=$true; if ($_.Exception.Message -notmatch $Pattern) { throw "Unexpected error: $($_.Exception.Message)" } }
    if (-not $caught) { throw 'Expected refusal did not occur' }
}
function Mock { param([string]$Name,[scriptblock]$Body)
    if (-not $script:mocks.ContainsKey($Name)) {
        $command=Get-Command $Name
        if ($command.CommandType -eq 'Function') { $script:mocks[$Name]=$command.ScriptBlock } else { $script:mocks[$Name]=$null }
    }
    Set-Item -Path "Function:script:$Name" -Value $Body
}
function Case { param([string]$Name,[scriptblock]$Action)
    try { & $Action; $script:passed++; Write-Host "PASS $Name" }
    catch { $script:failed++; Write-Host "FAIL $Name : $($_.Exception.Message)" -ForegroundColor Red }
    finally { foreach ($name in @($script:mocks.Keys)) { if ($script:mocks[$name]) { Set-Item -Path "Function:script:$name" -Value $script:mocks[$name] } else { Remove-Item -Path "Function:\$name" } }; $script:mocks=@{} }
}
function IsolatedRoot { param([string]$Name)
    Initialize-BusGoLocal (Join-Path $testRoot $Name)
    [IO.Directory]::CreateDirectory($script:Repo) | Out-Null
}
function EmptyDocker {
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90); return [pscustomobject]@{Code=0;Output=''} }
}
function MockOwnedDocker {
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
        $config=$script:testConfig
        $labels=@{'com.docker.compose.project'=$config.Project;'com.docker.compose.service'='mysql';'io.busgo.launcher.instance'=$config.Id;'io.busgo.launcher.config'=(Get-ConfigFingerprint $config)}
        if ($Arguments[0] -eq 'volume') { return [pscustomobject]@{Code=0;Output=(@{Labels=$labels} | ConvertTo-Json -Depth 6)} }
        if ($Arguments[0] -eq 'ps') { return [pscustomobject]@{Code=0;Output='container123'} }
        $password=$config.Password; if ($script:badCredentials) { $password='different' }
        $port='3308'; if ($script:badPort) { $port='3307' }
        $container=@{Id='container123';Config=@{Image='mysql:8.4';Labels=$labels;Env=@("MYSQL_DATABASE=$($config.Database)","MYSQL_USER=$($config.User)","MYSQL_PASSWORD=$password","MYSQL_ROOT_PASSWORD=$($config.RootPassword)")};Mounts=@(@{Destination='/var/lib/mysql';Name='busgo-local-demo_mysql_data'});HostConfig=@{PortBindings=@{'3306/tcp'=@(@{HostIp='127.0.0.1';HostPort=$port})}};State=@{Running=$true;Health=@{Status='healthy'}}}
        return [pscustomobject]@{Code=0;Output=($container | ConvertTo-Json -Depth 10)}
    }
}

Case 'PowerShell syntax for all production scripts' {
    foreach ($file in @('start-busgo.ps1','stop-busgo.ps1','scripts/local/Common.ps1','scripts/local/Worker.ps1')) {
        $tokens=$null; $errors=$null
        [Management.Automation.Language.Parser]::ParseFile((Join-Path $repoRoot $file),[ref]$tokens,[ref]$errors) | Out-Null
        Assert (@($errors).Count -eq 0) "Invalid syntax: $file"
    }
}
Case 'Missing prerequisite fails clearly' { Throws { Get-RequiredCommand 'busgo-nonexistent-tool-921.exe' } 'Missing.*Install' }
Case 'Java/Maven wrong versions are rejected' {
    Throws { Assert-VersionOutput ([pscustomobject]@{Code=0;Output='Java version: 1.8.0'}) 'Java version: 17\.' 'Maven must use Java 17' } 'Java 17'
    Throws { Assert-VersionOutput ([pscustomobject]@{Code=1;Output='v22.20.0'}) '^v22' 'Cannot execute Node' } 'Cannot execute'
}
Case 'Default Java 8 is skipped for an available portable JDK 17 without changing shell environment' {
    $javaHomeBefore=$env:JAVA_HOME; $pathBefore=$env:Path
    $script:fakeJdk8=Join-Path $testRoot 'jdk8'; $script:fakeJdk17=Join-Path $testRoot 'jdk17'
    foreach ($jdkFixtureRoot in @($script:fakeJdk8,$script:fakeJdk17)) {
        [IO.Directory]::CreateDirectory((Join-Path $jdkFixtureRoot 'bin')) | Out-Null
        [IO.File]::WriteAllText((Join-Path $jdkFixtureRoot 'bin/java.exe'),'fixture')
        [IO.File]::WriteAllText((Join-Path $jdkFixtureRoot 'bin/javac.exe'),'fixture')
    }
    Mock Get-JdkCandidates { @($script:fakeJdk8,$script:fakeJdk17) }
    Mock Invoke-LocalCommand { param($File,$Arguments,$Environment,$Timeout)
        if ($File.StartsWith($script:fakeJdk8)) { return [pscustomobject]@{Code=0;Output='java version "1.8.0"'} }
        Assert ($Environment.JAVA_HOME -eq $script:fakeJdk17 -and $Environment.PATH.StartsWith($script:fakeJdk17))
        if ($File.EndsWith('javac.exe')) { return [pscustomobject]@{Code=0;Output='javac 17.0.20'} }
        return [pscustomobject]@{Code=0;Output='openjdk version "17.0.20"'}
    }
    $selected=Find-Jdk17
    Assert ($selected.Home -eq $script:fakeJdk17)
    Assert ($env:JAVA_HOME -ceq $javaHomeBefore -and $env:Path -ceq $pathBefore) 'Launcher modified shell environment'
}
Case 'No valid Java 17 never falls back to Java 8' {
    Mock Get-JdkCandidates { @($script:fakeJdk8) }
    Mock Invoke-LocalCommand { [pscustomobject]@{Code=0;Output='java version "1.8.0"'} }
    Throws { Find-Jdk17 } 'JDK 17 was not found.*No software was downloaded'
}
Case 'First valid JDK 17 candidate wins and must include javac' {
    Mock Get-JdkCandidates { @($script:fakeJdk17,$script:fakeJdk8) }
    Mock Invoke-LocalCommand { param($File,$Arguments,$Environment,$Timeout)
        Assert ($File.StartsWith($script:fakeJdk17)) 'Ignored preferred valid JDK'
        if ($File.EndsWith('javac.exe')) { return [pscustomobject]@{Code=0;Output='javac 17.0.20'} }
        return [pscustomobject]@{Code=0;Output='openjdk version "17.0.20"'}
    }
    Assert ((Find-Jdk17).Home -eq $script:fakeJdk17)
}
Case 'Modern Compose major versions are accepted by version and required capabilities' {
    Mock Get-RequiredCommand { 'docker.exe' }
    Mock Invoke-LocalCommand { param($File,$Arguments,$Environment,$Timeout)
        $joined=$Arguments -join ' '
        if ($joined -eq 'context show') { return [pscustomobject]@{Code=0;Output='desktop-linux'} }
        if ($joined -like 'context inspect*') { return [pscustomobject]@{Code=0;Output='[{"Endpoints":{"docker":{"Host":"npipe:////./pipe/dockerDesktopLinuxEngine"}}}]'} }
        if ($joined -like '*{{.OSType}}*') { return [pscustomobject]@{Code=0;Output='linux'} }
        if ($joined -like '*{{.ID}}*') { return [pscustomobject]@{Code=0;Output='test-engine'} }
        if ($joined -like '*compose version*') { return [pscustomobject]@{Code=0;Output='5.5.1'} }
        return [pscustomobject]@{Code=0;Output='--wait-timeout'}
    }
    Assert ((Get-LocalDocker).Context -eq 'desktop-linux')
}
Case 'Launcher-only MySQL override supports trigger migrations without modifying shared Compose' {
    Initialize-BusGoLocal $repoRoot
    $composeArguments=Get-ComposeArgs
    Assert ($composeArguments -contains (Join-Path $repoRoot 'scripts/local/mysql-demo.yml'))
    $override=[IO.File]::ReadAllText((Join-Path $repoRoot 'scripts/local/mysql-demo.yml'))
    Assert ($override -match '--log-bin-trust-function-creators=1')
}
Case 'Failed Flyway history blocks backend startup and never performs repair' {
    $script:historyCommands=@()
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
        $script:historyCommands+=($Arguments -join ' ')
        if ($Arguments[4] -match 'information_schema') { return [pscustomobject]@{Code=0;Output="1`n1"} }
        return [pscustomobject]@{Code=0;Output='18'}
    }
    Throws { Assert-MySqlReadyForMigrations @{} ([pscustomobject]@{Id='fixture'}) } 'Failed Flyway migration.*18.*will not repair/reset'
    Assert (($script:historyCommands -join '|') -notmatch '(?i)\b(DELETE|UPDATE|REPAIR|DROP)\b')
}
Case 'Fresh schema and successful Flyway history allow migration preflight' {
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
        return [pscustomobject]@{Code=0;Output="1`n0"}
    }
    Assert-MySqlReadyForMigrations @{} ([pscustomobject]@{Id='fixture'})
}
Case 'Docker engine down is refused, without any compose startup' {
    Mock Get-RequiredCommand { 'docker.exe' }
    Mock Invoke-LocalCommand { param($File,$Arguments,$Environment,$Timeout)
        switch ($Arguments[0]) {
            'context' { if ($Arguments[1] -eq 'show') { return [pscustomobject]@{Code=0;Output='desktop-linux'} }; return [pscustomobject]@{Code=0;Output='[{"Endpoints":{"docker":{"Host":"npipe:////./pipe/dockerDesktopLinuxEngine"}}}]'} }
            default { return [pscustomobject]@{Code=1;Output='engine unavailable'} }
        }
    }
    Throws { Get-LocalDocker } 'engine is not ready'
}
Case 'Remote Docker contexts are refused before contacting engine' {
    Mock Get-RequiredCommand { 'docker.exe' }
    Mock Invoke-LocalCommand { param($File,$Arguments,$Environment,$Timeout)
        if ($Arguments[1] -eq 'show') { return [pscustomobject]@{Code=0;Output='remote'} }
        return [pscustomobject]@{Code=0;Output='[{"Endpoints":{"docker":{"Host":"ssh://remote.example"}}}]'}
    }
    Throws { Get-LocalDocker } 'Remote TCP/SSH Docker is refused'
}
Case 'Occupied service port does not kill or adopt a process' {
    Mock Get-PortOwners { @(12345) }
    Throws { Assert-FreePort 8080 } 'occupied.*Nothing was killed'
}
Case 'Port inspection failure is not mistaken for free port' {
    Mock Get-PortOwners { throw 'Access denied' }
    Throws { Assert-FreePort 8080 } 'Access denied'
}
Case 'First config: crypto secrets, private ACL, fixed isolation and restart reuse' {
    IsolatedRoot 'config'
    EmptyDocker
    $guard=Enter-LauncherLock
    try {
        $first=Get-LauncherConfig $script:fakeTools -Create
        Assert ($first.Port -eq 3308 -and $first.Project -eq 'busgo-local-demo' -and $first.Database -eq 'busgo_launcher_demo')
        Assert ($first.Password.Length -eq 64 -and $first.Password -ne $first.RootPassword)
        Assert ([Convert]::FromBase64String($first.Jwt).Length -eq 32)
        $fingerprint=Get-ConfigFingerprint $first
        $second=Get-LauncherConfig $script:fakeTools -Create
        Assert ($fingerprint -eq (Get-ConfigFingerprint $second)) 'Secrets changed on restart'
        Assert ((Get-Acl $script:Local).AreAccessRulesProtected) 'ACL inheritance still enabled'
        Assert (-not (Test-Path (Join-Path $script:Repo '.env'))) 'Touched root Compose env'
        $script:testConfig=$second
        $script:configRoot=$script:Repo
    } finally { $guard.Dispose() }
}
Case 'Concurrent start/stop is serialized by an exclusive file lock' {
    Initialize-BusGoLocal $script:configRoot
    $first=Enter-LauncherLock
    try { Throws { Enter-LauncherLock } 'Another start/stop' } finally { $first.Dispose() }
}
Case 'Existing compose credentials are never silently rewritten' {
    Initialize-BusGoLocal $script:configRoot
    $envPath=Join-Path $script:Local 'compose.env'
    $original=[IO.File]::ReadAllText($envPath)
    [IO.File]::AppendAllText($envPath,'MYSQL_PASSWORD=changed')
    try { Throws { Get-LauncherConfig $script:fakeTools } 'inconsistent' }
    finally { [IO.File]::WriteAllText($envPath,$original,[Text.UTF8Encoding]::new($false)) }
}
Case 'Missing config cannot claim an existing launcher volume' {
    IsolatedRoot 'lost-config'; $guard=Enter-LauncherLock
    try {
        Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
            return [pscustomobject]@{Code=0;Output='busgo-local-demo_mysql_data'}
        }
        Throws { Get-LauncherConfig $script:fakeTools -Create } 'Cannot recover'
        Assert (-not (Test-Path (Join-Path $script:Local 'config.json')))
    } finally { $guard.Dispose() }
}
Case 'Foreign volume rejected before connection or migration' {
    Initialize-BusGoLocal $script:configRoot
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
        return [pscustomobject]@{Code=0;Output='[{"Labels":{"com.docker.compose.project":"other","io.busgo.launcher.instance":"other","io.busgo.launcher.config":"other"}}]'}
    }
    Throws { Assert-DatabaseIdentity $script:fakeTools $script:testConfig } 'volume ownership/config mismatch'
}
Case 'Same project but wrong config fingerprint is refused' {
    Initialize-BusGoLocal $script:configRoot
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
        $v=@{Labels=@{'com.docker.compose.project'='busgo-local-demo';'io.busgo.launcher.instance'=$script:testConfig.Id;'io.busgo.launcher.config'='wrong'}}
        return [pscustomobject]@{Code=0;Output=($v | ConvertTo-Json -Depth 5)}
    }
    Throws { Assert-DatabaseIdentity $script:fakeTools $script:testConfig } 'volume ownership/config mismatch'
}
Case 'Owned stopped volume is inspected read-only and never removed' {
    Initialize-BusGoLocal $script:configRoot
    $script:dockerCalls=@()
    Mock Invoke-Docker { param($Tools,$Arguments,[switch]$AllowFailure,[int]$Timeout=90)
        $script:dockerCalls+=($Arguments -join ' ')
        if ($Arguments[0] -eq 'volume') {
            $v=@{Labels=@{'com.docker.compose.project'='busgo-local-demo';'io.busgo.launcher.instance'=$script:testConfig.Id;'io.busgo.launcher.config'=(Get-ConfigFingerprint $script:testConfig)}}
            return [pscustomobject]@{Code=0;Output=($v | ConvertTo-Json -Depth 5)}
        }
        return [pscustomobject]@{Code=0;Output=''}
    }
    $result=Assert-DatabaseIdentity $script:fakeTools $script:testConfig
    Assert ($null -eq $result)
    Assert (($script:dockerCalls -join '|') -notmatch '\b(rm|down|exec|up|stop)\b')
}
Case 'Owned MySQL accepts only matching image, labels, volume, port and credentials' {
    Initialize-BusGoLocal $script:configRoot
    $script:badCredentials=$false; $script:badPort=$false
    MockOwnedDocker
    $container=Assert-DatabaseIdentity $script:fakeTools $script:testConfig
    Assert ($container.Id -eq 'container123' -and $container.State.Health.Status -eq 'healthy')
    $script:badCredentials=$true
    Throws { Assert-DatabaseIdentity $script:fakeTools $script:testConfig } 'credentials differ'
    $script:badCredentials=$false; $script:badPort=$true
    Throws { Assert-DatabaseIdentity $script:fakeTools $script:testConfig } 'port mismatch'
}
Case 'Switching even to another local Docker engine cannot silently create a fresh database' {
    Initialize-BusGoLocal $script:configRoot
    Throws { Get-LauncherConfig @{Context='other-local';EngineId='another-engine'} } 'context/engine differs'
    Throws { Assert-DatabaseIdentity @{Context='desktop-linux';EngineId='another-engine'} $script:testConfig } 'ownership mismatch'
}
Case 'Stale worker without live PID can be recovered without killing anything' {
    Initialize-BusGoLocal $script:configRoot
    Mock Get-Process { param($Id,$ErrorAction); return $null }
    $r=[pscustomobject]@{Token=('a'*32);Service='backend';Root=$script:Repo;Job=('Local\BusGo-'+('a'*32));Pid=99999}
    Assert (-not (Assert-WorkerIdentity $r))
    Stop-OwnedService $r
    Stop-OwnedService $r
}
Case 'Reused PID refuses stop and start adoption' {
    Initialize-BusGoLocal $script:configRoot
    Mock Get-Process { param($Id,$ErrorAction); return [pscustomobject]@{StartTime=[DateTime]::Now;Path='other.exe'} }
    $r=[pscustomobject]@{Token=('a'*32);Service='backend';Root=$script:Repo;Job=('Local\BusGo-'+('a'*32));Pid=99999;StartTicks='0';Executable='launcher.exe'}
    Throws { Stop-OwnedService $r } 'PID has been reused'
}
Case 'Service listener outside managed job is never accepted' {
    Mock Assert-WorkerIdentity { $true }
    Mock Get-PortOwners { @($PID) }
    Throws { Assert-ServicePort ([pscustomobject]@{Service='backend';Job='Local\BusGo-nonexistent'}) 8080 } 'another process'
}
Case 'Start twice reuses owned worker and only starts the missing service' {
    Initialize-BusGoLocal $script:configRoot
    $script:createdCount=0; $script:fakeRecord=$null
    Mock Get-ServiceRecord { $script:fakeRecord }
    Mock Assert-WorkerIdentity { param($Record); return $null -ne $Record }
    Mock Assert-FreePort { }
    Mock Start-OwnedService { param($Service,$Tools,$Config)
        $script:createdCount++
        $script:fakeRecord=[pscustomobject]@{Service=$Service;ConfigHash=(Get-ConfigFingerprint $Config)}
        return $script:fakeRecord
    }
    $first=Ensure-OwnedService 'backend' 8080 @{} $script:testConfig
    $second=Ensure-OwnedService 'backend' 8080 @{} $script:testConfig
    Assert ($first.Created -and -not $second.Created -and $script:createdCount -eq 1)
}
Case 'Backend readiness uses real seeder names and requires future trip data' {
    Initialize-BusGoLocal $script:configRoot
    $source=[IO.File]::ReadAllText((Join-Path $repoRoot 'backend/src/main/java/com/busgo/demo/DemoDataSeeder.java'))
    $pickup=[regex]::Match($source,'new LocationSpec\("HCM", "([^"]+)"').Groups[1].Value
    $dropoff=[regex]::Match($source,'new LocationSpec\("DALAT", "([^"]+)"').Groups[1].Value
    $script:fixtureLocations=@([pscustomobject]@{id=100;name=$pickup},[pscustomobject]@{id=200;name=$dropoff})
    $token='d'*32; $log=Join-Path $script:Local "logs/backend-$token.log"
    [IO.Directory]::CreateDirectory((Split-Path $log)) | Out-Null
    [IO.File]::WriteAllText($log,'BusGo demo seed finished: fixture')
    Mock Assert-WorkerIdentity { $true }
    Mock Assert-ServicePort { $true }
    Mock Get-JsonResponse { param($Url)
        if ($Url -like '*/health') { return [pscustomobject]@{data=[pscustomobject]@{status='UP'}} }
        if ($Url -like '*/locations') { return [pscustomobject]@{data=$script:fixtureLocations} }
        Assert ($Url -match 'pickupLocationId=100&dropoffLocationId=200&departureDate=\d{4}-\d{2}-\d{2}') 'Incorrect trip search readiness request'
        return [pscustomobject]@{data=@([pscustomobject]@{tripId=1})}
    }
    Wait-Backend ([pscustomobject]@{Token=$token;Service='backend'}) -Timeout 2
}
Case 'Health without completed seed must not declare backend ready' {
    Initialize-BusGoLocal $script:configRoot
    Mock Assert-WorkerIdentity { $true }
    Mock Assert-ServicePort { $true }
    Mock Get-JsonResponse { throw 'HTTP must not be used as sole seed readiness' }
    Throws { Wait-Backend ([pscustomobject]@{Token=('e'*32);Service='backend'}) -Timeout 1 } 'readiness timed out'
}
Case 'Frontend HTML plus UP without DB proxy data is not ready' {
    Mock Assert-WorkerIdentity { $true }
    Mock Assert-ServicePort { $true }
    Mock Invoke-WebRequest { [pscustomobject]@{StatusCode=200;Content='<script src="/src/main.tsx"></script>'} }
    Mock Get-JsonResponse { param($Url)
        if ($Url -like '*/health') { return [pscustomobject]@{data=[pscustomobject]@{status='UP'}} }
        return [pscustomobject]@{data=$null}
    }
    Throws { Wait-Frontend ([pscustomobject]@{Service='frontend'}) -Timeout 1 } 'proxy readiness timed out'
}
Case 'Real HTTP JSON without charset preserves Vietnamese on Windows PowerShell 5.1' {
    if (-not ('BusGoJsonFixture' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'JsonFixture.cs') }
    $name='B'+[char]0x1ebf+'n xe '+[char]0x0110+[char]0x00e0+' L'+[char]0x1ea1+'t'
    $json=@{data=@{name=$name}} | ConvertTo-Json -Compress
    $fixture=[BusGoJsonFixture]::new($json)
    try {
        $decoded=Get-JsonResponse "http://127.0.0.1:$($fixture.Port)/locations"
        Assert ($decoded.data.name -ceq $name) 'Charset-free JSON was corrupted'
    } finally { $fixture.Dispose() }
}
Case 'Logs redact all known secret values and token/password text' {
    $secret=New-LocalSecret
    $text=[BusGoLocal.Native]::Redact("value $secret JWT_SECRET=abc Bearer xyz",[string[]]@($secret))
    Assert (-not $text.Contains($secret) -and -not $text.Contains('abc') -and -not $text.Contains('xyz'))
}
Case 'No automatic volume deletion or Flyway repair instructions in executable code' {
    $source=([IO.File]::ReadAllText((Join-Path $repoRoot 'start-busgo.ps1'))+[IO.File]::ReadAllText((Join-Path $repoRoot 'stop-busgo.ps1'))+[IO.File]::ReadAllText((Join-Path $repoRoot 'scripts/local/Common.ps1')))
    Assert ($source -notmatch 'down\s+-v|volume\s+rm|Invoke-Expression|taskkill|Stop-Process')
    Assert ($source -notmatch '(?im)^\s*(?:&\s+)?(?:mvn(?:\.cmd)?\s+.*flyway:repair|flyway(?:\.cmd)?\s+repair)')
    Assert ($source -match 'busgo-local-demo' -and $source -match '3308')
}
Case 'Native runner preserves internal JSON and redacts streamed logs' {
    Initialize-BusGoLocal $script:configRoot
    $shell=(Get-Process -Id $PID).Path
    $r=Invoke-LocalCommand $shell @('-NoProfile','-NonInteractive','-Command','Write-Output ''{"password":"example"}''')
    Assert ($r.Code -eq 0 -and ($r.Output | ConvertFrom-Json).password -eq 'example')
    $log=Join-Path $testRoot 'redaction.log'
    $r=[BusGoLocal.Native]::Run($shell,[string[]]@('-NoProfile','-NonInteractive','-Command','Write-Output ''password=privatevalue'''),$testRoot,@{},$log,[string[]]@('privatevalue'),15)
    Assert ($r.Code -eq 0 -and [IO.File]::ReadAllText($log) -notmatch 'privatevalue')
}
Case 'Native capture preserves UTF-8 Vietnamese output on Windows' {
    Initialize-BusGoLocal $script:configRoot
    $shell=(Get-Process -Id $PID).Path
    $command='[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); [Console]::Write([char]0x0111)'
    $result=Invoke-LocalCommand $shell @('-NoProfile','-NonInteractive','-Command',$command)
    Assert ($result.Code -eq 0 -and $result.Output.Trim() -ceq [string][char]0x0111) 'UTF-8 log output was corrupted'
}
if (-not $SkipProcessTest) {
    Case 'Real Windows job owns child process; unrelated owner refused; stop preserves files' {
        $token=[guid]::NewGuid().ToString('N'); $outputPath=Join-Path $testRoot 'job.json'
        $fixture=[BusGoLocal.Native]::Spawn((Get-Process -Id $PID).Path,[string[]]@('-NoProfile','-NonInteractive','-File',(Join-Path $PSScriptRoot 'job-fixture.ps1'),'-NativePath',(Join-Path $repoRoot 'scripts/local/Native.cs'),'-Token',$token,'-OutputPath',$outputPath),$testRoot)
        $job="Local\BusGo-$token"
        try {
            $deadline=[DateTime]::UtcNow.AddSeconds(30)
            while (-not (Test-Path $outputPath)) { if ($fixture.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw 'Job fixture initialization failed' }; Start-Sleep -Milliseconds 200 }
            $state=Read-LocalJson $outputPath
            Assert ([BusGoLocal.Native]::InJob($job,[int]$state.Pid))
            Assert ([BusGoLocal.Native]::InJob($job,[int]$state.ChildPid))
            Throws { [BusGoLocal.Native]::StopJob($job,$PID) } 'mismatch'
            Assert (-not $fixture.HasExited) 'Foreign owner request killed fixture'
            [BusGoLocal.Native]::StopJob($job,[int]$state.Pid)
            Assert ($fixture.WaitForExit(10000))
            $deadline=[DateTime]::UtcNow.AddSeconds(10)
            while ((Get-Process -Id $state.ChildPid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
            Assert (-not (Get-Process -Id $state.ChildPid -ErrorAction SilentlyContinue)) 'Child process survived stop'
            Assert (Test-Path $outputPath) 'Stop removed state/data file'
        } finally {
            if (-not $fixture.HasExited -and [BusGoLocal.Native]::InJob($job,$fixture.Id)) { [BusGoLocal.Native]::StopJob($job,$fixture.Id) }
        }
    }
}
Write-Host "Launcher tests: $script:passed passed, $script:failed failed. Test files retained under .tools/launcher-tests/."
if ($script:failed) { exit 1 }
