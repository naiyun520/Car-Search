$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Logs = Join-Path $Root '.local\logs'
$Repo = Join-Path $Root '.local\maven-repo'
$PidFile = Join-Path $Root '.local\backend.pid'
$EnvFile = Join-Path $Root '.env.local'
New-Item -ItemType Directory -Force -Path $Logs | Out-Null
if (-not (Test-Path -LiteralPath $EnvFile)) { throw '.env.local is missing' }
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2], 'Process')
    }
}
$Listening = netstat -ano -p TCP | Select-String -Pattern '^\s*TCP\s+[^\s]*:8080\s+[^\s]+\s+LISTENING\s+(\d+)\s*$'
if (-not $Listening) {
    Push-Location (Join-Path $Root 'backend')
    try {
        & mvn "-Dmaven.repo.local=$Repo" -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed' }
    } finally { Pop-Location }
    $Jar = Join-Path $Root 'backend\target\car-search-backend-0.1.0-SNAPSHOT.jar'
    $Process = Start-Process -FilePath 'java.exe' -ArgumentList '-jar',$Jar -WorkingDirectory $Root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Logs 'backend.stdout.log') -RedirectStandardError (Join-Path $Logs 'backend.stderr.log')
    Set-Content -LiteralPath $PidFile -Value $Process.Id -Encoding ascii
}
for ($Attempt = 0; $Attempt -lt 30; $Attempt++) {
    try {
        $Health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
        if ($Health.status -eq 'UP') {
            $Line = netstat -ano -p TCP | Select-String -Pattern '^\s*TCP\s+[^\s]*:8080\s+[^\s]+\s+LISTENING\s+(\d+)\s*$' | Select-Object -First 1
            if ($Line -and $Line.Matches.Count -gt 0) { Set-Content -LiteralPath $PidFile -Value $Line.Matches[0].Groups[1].Value -Encoding ascii }
            Write-Host 'Backend is ready at http://127.0.0.1:8080'
            exit 0
        }
    } catch {}
    Start-Sleep -Seconds 1
}
throw "Backend failed to become ready. See logs in $Logs"
