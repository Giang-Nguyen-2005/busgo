[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'scripts/local/Common.ps1')
$lock=$null
try {
    Initialize-BusGoLocal $PSScriptRoot
    if (-not (Test-Path -LiteralPath (Join-Path $script:Local 'config.json'))) { Write-Host 'No launcher config; nothing was stopped.'; exit 0 }
    $lock=Enter-LauncherLock
    $config=Get-LauncherConfig $null
    Write-LauncherLog 'Stop: verifying worker identities.'
    # Verify every live worker before changing anything. PID reuse is a hard failure.
    $frontend=Get-ServiceRecord 'frontend'; $backend=Get-ServiceRecord 'backend'
    foreach ($record in @($frontend,$backend)) { $null=Assert-WorkerIdentity $record }
    foreach ($entry in @(@($frontend,5173),@($backend,8080))) {
        $owners=@(Get-PortOwners $entry[1])
        if ($owners.Count -and -not (Assert-WorkerIdentity $entry[0])) { throw "Port $($entry[1]) has an unmanaged listener. Nothing was stopped." }
        foreach ($owner in $owners) { if (-not [BusGoLocal.Native]::InJob($entry[0].Job,[int]$owner)) { throw 'A port belongs to another process. Nothing was stopped.' } }
    }
    # Docker may be unavailable: stop verified application jobs anyway, then report
    # that MySQL could not be inspected/stopped. Never stop Docker Desktop.
    Stop-OwnedService $frontend
    Stop-OwnedService $backend
    $tools=Get-LocalDocker
    $container=Assert-DatabaseIdentity $tools $config
    if ($container -and $container.State.Running) { $null=Invoke-Docker $tools @('stop','--time','30',$container.Id) }
    Write-LauncherLog 'Stopped verified launcher services; volume/config/logs retained.'
    Write-Host 'BusGo launcher services stopped. MySQL volume, config, secrets and logs were kept.'
    exit 0
} catch {
    Write-Host "BusGo stop refused or incomplete: $($_.Exception.Message)" -ForegroundColor Red
    if ($lock) { try { Write-LauncherLog 'Stop refused or incomplete. Inspect state/ownership and Docker availability; no data deleted.' } catch { } }
    exit 1
} finally { if ($lock) { $lock.Dispose() } }
