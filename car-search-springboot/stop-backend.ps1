$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFile = Join-Path $Root '.local\backend.pid'
$Ids = @()
if (Test-Path -LiteralPath $PidFile) {
    $Value = (Get-Content -LiteralPath $PidFile -Raw).Trim()
    if ($Value -match '^\d+$') { $Ids += [int]$Value }
}
$Lines = netstat -ano -p TCP | Select-String -Pattern '^\s*TCP\s+[^\s]*:8080\s+[^\s]+\s+LISTENING\s+(\d+)\s*$'
foreach ($Line in $Lines) { if ($Line.Matches.Count -gt 0) { $Ids += [int]$Line.Matches[0].Groups[1].Value } }
$Ids = @($Ids | Select-Object -Unique)
if ($Ids.Count -eq 0) { Write-Host 'Backend is not listening on port 8080.'; exit 0 }
foreach ($ProcessId in $Ids) {
    $OwnsPort = netstat -ano -p TCP | Select-String -Pattern "^\s*TCP\s+[^\s]*:8080\s+[^\s]+\s+LISTENING\s+$ProcessId\s*$"
    if ($OwnsPort) { Stop-Process -Id $ProcessId -Force -ErrorAction Stop; Write-Host "Backend stopped (PID $ProcessId)" }
}
Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
