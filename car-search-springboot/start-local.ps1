$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Local = Join-Path $Root '.local'
$MySql = 'E:\mySQL\bin\mysqld.exe'
$MySqlAdmin = 'E:\mySQL\bin\mysqladmin.exe'
$Config = Join-Path $Local 'mysql-dev.ini'
$LogDir = Join-Path $Local 'logs'

function Test-MySqlReady {
    # Windows PowerShell converts native stderr into ErrorRecord objects.  A
    # failed mysqladmin ping is expected while the server is still stopped, so
    # do not let the script-wide Stop preference abort before we can start it.
    $PreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $MySqlAdmin --protocol=TCP --host=127.0.0.1 --port=3307 --user=root ping 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $PreviousPreference
    }
}

New-Item -ItemType Directory -Force -Path $LogDir,(Join-Path $Local 'mysql-files'),(Join-Path $Local 'maven-repo'),(Join-Path $Local 'npm-cache') | Out-Null
if (-not (Test-MySqlReady)) {
    Start-Process -FilePath $MySql -ArgumentList "--defaults-file=$Config",'--console' -WindowStyle Hidden -RedirectStandardOutput (Join-Path $LogDir 'mysql.stdout.log') -RedirectStandardError (Join-Path $LogDir 'mysql.stderr.log')
    Start-Sleep -Seconds 5
}
if (-not (Test-MySqlReady)) {
    throw "MySQL failed to start. See logs in $LogDir"
}
Write-Host 'MySQL is ready at 127.0.0.1:3307'
Write-Host 'Backend: load variables from .env.local, then run Maven in backend.'
Write-Host 'Miniapp: run npm run dev:mp-weixin in miniapp.'
