$ErrorActionPreference='Stop'
$TaskRoot=Split-Path -Parent $PSScriptRoot
$TaskRuntime=Join-Path $TaskRoot '.local/fulfillment-test'
$Config=Get-Content -Raw (Join-Path $TaskRuntime 'runtime.json') | ConvertFrom-Json
if(-not(Get-NetTCPConnection -State Listen -LocalPort 13316 -ErrorAction SilentlyContinue)){
  if(-not(Test-Path (Join-Path $TaskRuntime 'mysql/auto.cnf'))){throw 'Initialize the isolated MySQL and import the test dump first. Do not use another database.'}
  Start-Process -FilePath 'E:/mySQL/bin/mysqld.exe' -ArgumentList '--no-defaults','--basedir=E:/mySQL',"--datadir=$TaskRuntime/mysql",'--port=13316','--bind-address=127.0.0.1','--mysqlx=0','--console' -WindowStyle Hidden -RedirectStandardOutput (Join-Path $TaskRuntime 'mysql.stdout.log') -RedirectStandardError (Join-Path $TaskRuntime 'mysql.stderr.log') | Out-Null
  Start-Sleep -Seconds 3
}
if(-not(Get-NetTCPConnection -State Listen -LocalPort 11025 -ErrorAction SilentlyContinue)){
  $Fake=Start-Process -FilePath 'node.exe' -ArgumentList (Join-Path $TaskRoot 'tools/unified-fulfillment-local-test.cjs'),'serve-mail' -WorkingDirectory $TaskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $TaskRuntime 'mail.stdout.log') -RedirectStandardError (Join-Path $TaskRuntime 'mail.stderr.log')
  $Fake.Id | Set-Content (Join-Path $TaskRuntime 'mail.pid')
}
if(Get-NetTCPConnection -State Listen -LocalPort 18081 -ErrorAction SilentlyContinue){Write-Output 'Isolated backend already listens on port 18081';exit 0}
$env:DATA_ENCRYPT_KEY=$Config.key
$env:DB_URL='jdbc:mysql://127.0.0.1:13316/car_search_unified_test?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD=''
$env:CAR_LOCAL_TEST_ONLY='true'
$env:CAR_DEV_LOGIN_ENABLED='true'
$Process=Start-Process -FilePath 'java.exe' -ArgumentList '-Duser.timezone=Asia/Shanghai','-Dcar.local-test-only=true','-jar',(Join-Path $TaskRoot 'car-search-springboot/backend/target/car-search-backend-0.1.0-SNAPSHOT.jar'),'--spring.profiles.active=isolated-test' -WorkingDirectory $TaskRuntime -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $TaskRuntime 'backend.stdout.log') -RedirectStandardError (Join-Path $TaskRuntime 'backend.stderr.log')
$Process.Id | Set-Content (Join-Path $TaskRuntime 'backend.pid')
Write-Output 'Starting isolated backend: http://127.0.0.1:18081/localqa/ (loopback only)'
