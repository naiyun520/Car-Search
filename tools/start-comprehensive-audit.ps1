$ErrorActionPreference='Stop'
$TaskRoot=Split-Path -Parent $PSScriptRoot
$TaskRuntime=Join-Path $TaskRoot '.local/audit-20261104'
if(-not(Test-Path -LiteralPath (Join-Path $TaskRuntime 'baseline.json'))){throw 'Prepare the isolated audit database first'}
$TaskConfig=Get-Content -Raw (Join-Path $TaskRoot '.local/fulfillment-test/runtime.json') | ConvertFrom-Json
$TaskClasspath=(Join-Path $TaskRoot 'car-search-springboot/backend/target/test-classes')+';'+(Join-Path $TaskRoot 'car-search-springboot/backend/target/classes')+';'+(Get-Content -Raw (Join-Path $TaskRuntime 'classpath.txt')).Trim()
if(Get-NetTCPConnection -State Listen -LocalPort 18082 -ErrorAction SilentlyContinue){Write-Output 'Audit server already running';exit 0}
$env:DATA_ENCRYPT_KEY=$TaskConfig.key
$TaskProcess=Start-Process -FilePath 'java.exe' -ArgumentList '-Xms256m','-Xmx1024m','-XX:ActiveProcessorCount=4','-Duser.timezone=Asia/Shanghai',"-Daudit.runtime=$TaskRuntime",'-cp',('"'+$TaskClasspath+'"'),'audit.carsearch.LocalAuditServer' -WorkingDirectory $TaskRuntime -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $TaskRuntime 'server.stdout.log') -RedirectStandardError (Join-Path $TaskRuntime 'server.stderr.log')
$TaskProcess.Id | Set-Content (Join-Path $TaskRuntime 'server.pid')
$TaskProcess.ProcessorAffinity=15
Write-Output ('Audit JVM starting on four logical CPUs with 1024MB heap; PID '+$TaskProcess.Id)
