param([string]$NativePath,[string]$Token,[string]$OutputPath)
$ErrorActionPreference='Stop'
Add-Type -Path $NativePath
$job="Local\BusGo-$Token"
[BusGoLocal.Native]::OwnJob($job)
$child=[BusGoLocal.Native]::Spawn((Get-Process -Id $PID).Path,[string[]]@('-NoProfile','-NonInteractive','-Command','Start-Sleep -Seconds 120'),$PSScriptRoot)
[IO.File]::WriteAllText($OutputPath,(@{Pid=$PID;ChildPid=$child.Id;Job=$job} | ConvertTo-Json))
Start-Sleep -Seconds 120
