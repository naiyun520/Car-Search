$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFile = Join-Path $Root '.local\mysql.pid'
if (Test-Path -Path $PidFile) {
    $ProcessId = [int](Get-Content -Path $PidFile -Raw)
    Stop-Process -Id $ProcessId -ErrorAction SilentlyContinue
    Write-Host "Local MySQL stopped (PID $ProcessId)"
} else {
    Write-Host 'Local MySQL PID file was not found.'
}
