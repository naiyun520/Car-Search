param(
    [string]$AdminUsername = '',
    [string]$AdminPassword = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Mysql = 'E:\mySQL\bin\mysql.exe'
$Base = 'http://127.0.0.1:8080'
$Protocol = '20260815.2'
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
Assert-True ((Get-Status '/') -eq 302) 'Root redirects to admin console'
Assert-True ((Get-Status '/admin/') -eq 200) 'Admin static application is available'
Assert-True ((Get-Status '/route-that-must-not-exist') -eq 404) 'Unknown route returns HTTP 404'

$Bootstrap = Invoke-Api GET '/api/v1/bootstrap'
Assert-True ($Bootstrap.code -eq 0 -and $Bootstrap.data.services.Count -gt 0) 'Bootstrap returns enabled services'
Assert-True ((Get-Status '/api/v1/me') -eq 401) 'Protected user API rejects anonymous request'

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
    $Feedback = Invoke-Api GET '/api/v1/feedback?page=1' $UserHeaders
    Assert-True ($Feedback.code -eq 0 -and $null -ne $Feedback.data.data) 'User feedback list contract is valid'
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
    foreach ($Path in @('/admin-api/dashboard','/admin-api/dashboard-finance','/admin-api/users?page=1','/admin-api/orders?page=1','/admin-api/wechat-messages?page=1','/admin-api/services','/admin-api/announcements','/admin-api/settings','/admin-api/payment-settings','/admin-api/email-settings','/admin-api/feedback?page=1')) {
        $Response = Invoke-Api GET $Path $AdminHeaders
        Assert-True ($Response.code -eq 0) "Admin endpoint $Path succeeds"
    }
    $ServiceJson = (Invoke-Api GET '/admin-api/services' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
    Assert-True (-not $ServiceJson.Contains('request_url_cipher')) 'Admin service response does not expose encrypted provider URL storage'
    $MailJson = (Invoke-Api GET '/admin-api/email-settings' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
    Assert-True (-not $MailJson.Contains('smtp_password_cipher')) 'Admin email settings do not expose SMTP password ciphertext'
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
            foreach ($Path in @('/admin-api/dashboard','/admin-api/dashboard-finance','/admin-api/users?page=1','/admin-api/orders?page=1','/admin-api/wechat-messages?page=1','/admin-api/services','/admin-api/announcements','/admin-api/settings','/admin-api/payment-settings','/admin-api/email-settings','/admin-api/feedback?page=1')) {
                $Response = Invoke-Api GET $Path $AdminHeaders
                Assert-True ($Response.code -eq 0) "Admin endpoint $Path succeeds"
            }
            $ServiceJson = (Invoke-Api GET '/admin-api/services' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
            Assert-True (-not $ServiceJson.Contains('request_url_cipher')) 'Admin service response does not expose encrypted provider URL storage'
            $MailJson = (Invoke-Api GET '/admin-api/email-settings' $AdminHeaders).data | ConvertTo-Json -Depth 8 -Compress
            Assert-True (-not $MailJson.Contains('smtp_password_cipher')) 'Admin email settings do not expose SMTP password ciphertext'
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
