$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $Root '.env.local'
$Repo = Join-Path $Root '.local\maven-repo'

if (-not (Test-Path -Path $EnvFile)) { throw '.env.local is missing' }
Get-Content -Path $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2], 'Process')
    }
}
New-Item -ItemType Directory -Force -Path $Repo | Out-Null
Set-Location (Join-Path $Root 'backend')
& mvn "-Dmaven.repo.local=$Repo" spring-boot:run
