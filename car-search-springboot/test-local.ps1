param(
    [string]$AdminUsername = '',
    [string]$AdminPassword = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Mysql = 'E:\mySQL\bin\mysql.exe'
$Base = 'http://127.0.0.1:8080'
$Protocol = '20260921.1'
$Passed = 0
$LocalEnvironment = @{}
Get-Content -LiteralPath (Join-Path $Root '.env.local') | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $LocalEnvironment[$Matches[1].Trim()] = $Matches[2] }
}
$DatabaseUrl = [string]$LocalEnvironment.DB_URL
if ($DatabaseUrl -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)') { throw 'DB_URL in .env.local is not a supported MySQL JDBC URL' }
$DatabaseHost = $Matches[1]
$DatabasePort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$DatabaseName = $Matches[3]
$DatabaseUser = [string]$LocalEnvironment.DB_USERNAME
$DatabasePassword = [string]$LocalEnvironment.DB_PASSWORD
if (-not $DatabaseUser) { throw 'DB_USERNAME is missing from .env.local' }
$MysqlConnection = @('--protocol=TCP', "--host=$DatabaseHost", "--port=$DatabasePort", "--user=$DatabaseUser", "--password=$DatabasePassword", "--database=$DatabaseName")

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "TEST FAILED: $Message" }
    $script:Passed++
    Write-Host "PASS  $Message"
}

function Invoke-Api([string]$Method, [string]$Path, [hashtable]$Headers = @{}, $Body = $null) {
    $Arguments = @{ Uri = "$Base$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 10 }
    if ($null -ne $Body) {
        $Arguments.ContentType = 'application/json; charset=utf-8'
        $Arguments.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @Arguments
}

function Get-Status([string]$Path, [hashtable]$Headers = @{}) {
    $Handler = New-Object System.Net.Http.HttpClientHandler
    $Handler.AllowAutoRedirect = $false
    $Client = New-Object System.Net.Http.HttpClient($Handler)
    $Client.Timeout = [TimeSpan]::FromSeconds(10)
    try {
        $Request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "$Base$Path")
        foreach ($Name in $Headers.Keys) { [void]$Request.Headers.TryAddWithoutValidation($Name, [string]$Headers[$Name]) }
        $Response = $Client.SendAsync($Request).GetAwaiter().GetResult()
        return [int]$Response.StatusCode
    } finally {
        if ($Request) { $Request.Dispose() }
        if ($Response) { $Response.Dispose() }
        $Client.Dispose()
        $Handler.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $Mysql)) { throw "MySQL client not found: $Mysql" }
$Health = Invoke-RestMethod -Uri "$Base/actuator/health" -TimeoutSec 10
Assert-True ($Health.status -eq 'UP') 'Spring Boot health is UP'
$ConfiguredAdminPath = (& $Mysql @MysqlConnection --batch --skip-column-names --execute='select value from ci_setting where `key`=''admin_path'' limit 1').Trim()
if (-not $ConfiguredAdminPath) { $ConfiguredAdminPath = 'admin' }
Assert-True ((Get-Status '/') -eq 404) 'Root domain does not expose or redirect to admin console'
Assert-True ((Get-Status "/$ConfiguredAdminPath") -eq 404) 'Admin console requires the configured path with trailing slash'
Assert-True ((Get-Status "/$ConfiguredAdminPath/") -eq 200) 'Admin static application is available'
Assert-True ((Get-Status '/route-that-must-not-exist') -eq 404) 'Unknown route returns HTTP 404'

$TemporaryAdminPath = 'contract-admin'
try {
    & $Mysql @MysqlConnection --execute="insert into ci_setting(``key``,value,updated_at) values('admin_path','$TemporaryAdminPath',now()) on duplicate key update value=values(value),updated_at=values(updated_at)" | Out-Null
    Assert-True ((Get-Status "/$ConfiguredAdminPath/") -eq 404) 'Changing admin path immediately disables the old address'
    Assert-True ((Get-Status "/$TemporaryAdminPath/") -eq 200) 'Changing admin path immediately enables the new address'
} finally {
    & $Mysql @MysqlConnection --execute="update ci_setting set value='$ConfiguredAdminPath',updated_at=now() where ``key``='admin_path'" | Out-Null
}

$Bootstrap = Invoke-Api GET '/api/v1/bootstrap'
Assert-True ($Bootstrap.code -eq 0 -and $Bootstrap.data.services.Count -gt 0) 'Bootstrap returns enabled services'
Assert-True ($Bootstrap.data.categories.Count -gt 0 -and $null -ne $Bootstrap.data.services[0].sale_price_ios) 'Bootstrap returns categories and per-platform prices'
Assert-True ((Get-Status '/api/v1/me') -eq 401) 'Protected user API rejects anonymous request'

if ([string]$LocalEnvironment.CAR_DEV_LOGIN_ENABLED -eq 'true') {
    $DevLogin = Invoke-Api POST '/api/v1/auth/login' @{ 'X-Car-Client-Version' = $Protocol } @{ code = 'local-integration-test' }
    Assert-True ($DevLogin.code -eq 0 -and $DevLogin.data.token -and $DevLogin.data.user.id) 'Loopback-only development login creates a local session'
    $DevHeaders = @{ Authorization = "Bearer $($DevLogin.data.token)"; 'X-Car-Client-Version' = $Protocol }
    $DevMe = Invoke-Api GET '/api/v1/me' $DevHeaders
    Assert-True ($DevMe.code -eq 0 -and "$($DevMe.data.id)" -eq "$($DevLogin.data.user.id)") 'Development login can open the My page API'
    $DevLogout = Invoke-Api POST '/api/v1/auth/logout' $DevHeaders @{}
    Assert-True ($DevLogout.code -eq 0) 'Development login token can be revoked'
}

$UserId = (& $Mysql @MysqlConnection --batch --skip-column-names --execute="select id from ci_user where status=1 order by id limit 1").Trim()
Assert-True ($UserId -match '^\d+$') 'A local active test user exists'
$RawToken = -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
& $Mysql @MysqlConnection --execute="insert into ci_user_token(user_id,token_hash,expires_at,created_at) values($UserId,sha2('$RawToken',256),date_add(now(),interval 10 minute),now())" | Out-Null
$UserHeaders = @{ Authorization = "Bearer $RawToken"; 'X-Car-Protocol' = $Protocol }
try {
    $Me = Invoke-Api GET '/api/v1/me' $UserHeaders
    Assert-True ($Me.code -eq 0 -and "$($Me.data.id)" -eq $UserId) 'Seeded bearer token authenticates the correct user'
    $Orders = Invoke-Api GET '/api/v1/orders?page=1&page_size=10' $UserHeaders
    Assert-True ($Orders.code -eq 0 -and $null -ne $Orders.data.data) 'User order list contract is valid'
    if ($Orders.data.data.Count -gt 0) {
        $SerializedOrder = $Orders.data.data[0] | ConvertTo-Json -Depth 8 -Compress
        Assert-True (-not $SerializedOrder.Contains('input_cipher') -and -not $SerializedOrder.Contains('result_cipher')) 'User order response does not expose encrypted sensitive columns'
    }
    Assert-True ((Get-Status '/api/v1/feedback' $UserHeaders) -eq 404) 'Removed user feedback API is unavailable'
    $Logout = Invoke-Api POST '/api/v1/auth/logout' $UserHeaders @{}
    Assert-True ($Logout.code -eq 0) 'User logout succeeds'
    Assert-True ((Get-Status '/api/v1/me' $UserHeaders) -eq 401) 'Logged-out user token is immediately revoked'
} finally {
    & $Mysql @MysqlConnection --execute="delete from ci_user_token where token_hash=sha2('$RawToken',256)" | Out-Null
}

$Counts = (& $Mysql @MysqlConnection --batch --skip-column-names --execute="select (select count(*) from ci_order),(select count(*) from ci_payment),(select count(*) from ci_service),(select count(*) from ci_user)").Trim().Split("`t")
Assert-True ($Counts.Count -eq 4 -and [int]$Counts[0] -gt 0 -and [int]$Counts[1] -gt 0 -and [int]$Counts[2] -gt 0 -and [int]$Counts[3] -gt 0) 'Imported production-shaped database is readable'

if ($AdminUsername -and $AdminPassword) {
    $Login = Invoke-Api POST '/admin-api/login' @{} @{ username = $AdminUsername; password = $AdminPassword }
    Assert-True ($Login.code -eq 0 -and $Login.data.token) 'Administrator login uses JSON body and returns a token'
    $AdminHeaders = @{ Authorization = "Bearer $($Login.data.token)" }
    Assert-True ((Get-Status '/admin-api/feedback' $AdminHeaders) -eq 404) 'Removed admin feedback API is unavailable'
    foreach ($Path in @('/admin-api/dashboard','/admin-api/dashboard-finance','/admin-api/users?page=1','/admin-api/orders?page=1','/admin-api/wechat-messages?page=1','/admin-api/services','/admin-api/service-categories','/admin-api/encryption-health','/admin-api/announcements','/admin-api/settings','/admin-api/payment-settings','/admin-api/email-settings')) {
        $Response = Invoke-Api GET $Path $AdminHeaders
        Assert-True ($Response.code -eq 0) "Admin endpoint $Path succeeds"
    }
    $ServiceJson = (Invoke-Api GET '/admin-api/services' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
    Assert-True (-not $ServiceJson.Contains('request_url_cipher')) 'Admin service response does not expose encrypted provider URL storage'
    $PaymentSettings = (Invoke-Api GET '/admin-api/payment-settings' $AdminHeaders).data
    Assert-True ($null -ne $PaymentSettings.PSObject.Properties['android_auto_refund_query_failed']) 'Admin payment settings expose the Android automatic-refund switch'
    $MailJson = (Invoke-Api GET '/admin-api/email-settings' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
    Assert-True (-not $MailJson.Contains('smtp_password_cipher')) 'Admin email settings do not expose SMTP password ciphertext'
    Assert-True ($MailJson.Contains('email_notify_enabled') -and $MailJson.Contains('email_event_refund_result') -and $MailJson.Contains('email_event_provider_balance')) 'Admin email settings expose the master switch and event selections'
    $AdminLogout = Invoke-Api POST '/admin-api/logout' $AdminHeaders @{}
    Assert-True ($AdminLogout.code -eq 0) 'Administrator logout succeeds'
} else {
    $AdminRow = (& $Mysql @MysqlConnection --batch --skip-column-names --execute="select id,username from ci_admin where status=1 and must_change_password=0 order by id limit 1").Trim()
    if ($AdminRow) {
        $AdminParts = $AdminRow.Split("`t", 2)
        $AdminId = $AdminParts[0]
        $AdminToken = -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
        & $Mysql @MysqlConnection --execute="insert into ci_admin_token(admin_id,token_hash,expires_at,created_at) values($AdminId,sha2('$AdminToken',256),date_add(now(),interval 10 minute),now())" | Out-Null
        $AdminHeaders = @{ Authorization = "Bearer $AdminToken" }
        try {
            Assert-True ((Get-Status '/admin-api/feedback' $AdminHeaders) -eq 404) 'Removed admin feedback API is unavailable'
            foreach ($Path in @('/admin-api/dashboard','/admin-api/dashboard-finance','/admin-api/users?page=1','/admin-api/orders?page=1','/admin-api/wechat-messages?page=1','/admin-api/services','/admin-api/service-categories','/admin-api/encryption-health','/admin-api/announcements','/admin-api/settings','/admin-api/payment-settings','/admin-api/email-settings')) {
                $Response = Invoke-Api GET $Path $AdminHeaders
                Assert-True ($Response.code -eq 0) "Admin endpoint $Path succeeds"
            }
            $ServiceJson = (Invoke-Api GET '/admin-api/services' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
            Assert-True (-not $ServiceJson.Contains('request_url_cipher')) 'Admin service response does not expose encrypted provider URL storage'
            $PaymentSettings = (Invoke-Api GET '/admin-api/payment-settings' $AdminHeaders).data
            Assert-True ($null -ne $PaymentSettings.PSObject.Properties['android_auto_refund_query_failed']) 'Admin payment settings expose the Android automatic-refund switch'
            $MailJson = (Invoke-Api GET '/admin-api/email-settings' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
            Assert-True (-not $MailJson.Contains('smtp_password_cipher')) 'Admin email settings do not expose SMTP password ciphertext'
            Assert-True ($MailJson.Contains('email_notify_enabled') -and $MailJson.Contains('email_event_refund_result') -and $MailJson.Contains('email_event_provider_balance')) 'Admin email settings expose the master switch and event selections'
            $AdminLogout = Invoke-Api POST '/admin-api/logout' $AdminHeaders @{}
            Assert-True ($AdminLogout.code -eq 0) 'Seeded administrator logout succeeds'
            Assert-True ((Get-Status '/admin-api/dashboard' $AdminHeaders) -eq 401) 'Logged-out administrator token is immediately revoked'
        } finally {
            & $Mysql @MysqlConnection --execute="delete from ci_admin_token where token_hash=sha2('$AdminToken',256)" | Out-Null
        }
    } else {
        Write-Host 'SKIP  Admin authenticated checks (no active administrator with completed password setup)'
    }
}

Write-Host "Local integration suite completed: $Passed checks passed."
