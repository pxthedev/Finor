param([ValidateRange(3, 20)][int]$Runs = 5)
$ErrorActionPreference = 'Stop'
$authEndpoint = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1'
$apiEndpoint = 'http://127.0.0.1:8080/api/v1'
# Isolated emulator account only. Real users and their records are never modified.
$created = Invoke-RestMethod -Method Post -Uri "$authEndpoint/accounts:signUp?key=demo-key" -ContentType application/json -Body (@{
    email = ('performance-' + [guid]::NewGuid().ToString('N') + '@example.test')
    password = [guid]::NewGuid().ToString('N')
    returnSecureToken = $true
} | ConvertTo-Json)
$headers = @{ Authorization = 'Bearer ' + $created.idToken }
$bootstrapped = $false
function Get-Api([string]$Path) {
    Invoke-RestMethod -Uri ($apiEndpoint + $Path) -Headers $headers -TimeoutSec 30
}
function Post-Api([string]$Path, $Body) {
    Invoke-RestMethod -Method Post -Uri ($apiEndpoint + $Path) -Headers $headers -ContentType application/json -Body ($Body | ConvertTo-Json -Depth 8) -TimeoutSec 30
}
function Measure-Endpoint([string]$Path) {
    $null = Get-Api $Path
    $samples = @(for ($index = 0; $index -lt $Runs; $index++) {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $null = Get-Api $Path
        $timer.Stop()
        $timer.Elapsed.TotalMilliseconds
    }) | Sort-Object
    [math]::Round($samples[[int][math]::Floor($samples.Count / 2)], 1)
}
try {
    $null = Post-Api '/auth/bootstrap' @{}
    $bootstrapped = $true
    $account = Post-Api '/accounts' @{ name = 'Conta de teste'; type = 'CHECKING'; openingBalance = '1000.00' }
    $categories = Get-Api '/categories'
    $category = $categories | Select-Object -First 1
    $today = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([datetime]::UtcNow, 'E. South America Standard Time')
    $month = $today.ToString('yyyy-MM')
    $null = Post-Api '/planning/schedule' @{ monthlyAmount = '100.00'; nextIncome = $today.AddMonths(1).ToString('yyyy-MM-dd'); remainingEssentials = 0; safetyMargin = 0 }
    foreach ($entry in @(@{ type = 'EXPENSE'; amount = '25.00' }, @{ type = 'INCOME'; amount = '100.00' })) {
        $headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
        $null = Post-Api '/transactions' @{ accountId = $account.id; categoryId = $category.id; type = $entry.type; amount = $entry.amount; date = $today.ToString('yyyy-MM-dd'); description = 'Verificação de desempenho' }
    }
    $headers.Remove('Idempotency-Key')
    $homeOverview = Get-Api ("/dashboard/home?month=$month")
    $report = Get-Api ("/reports/overview?month=$month")
    if ([decimal]$homeOverview.accounts[0].balance -ne 1075 -or [decimal]$report.summary.expenses -ne 25 -or [decimal]$report.summary.income -ne 100 -or [decimal]$report.allTimeExpenses[0].spent -ne 25) {
        throw 'Os totais dos novos endpoints não correspondem aos lançamentos de teste.'
    }
    $result = [ordered]@{
        homeRequestsBefore = 6
        homeRequestsAfter = 1
        homeMedianMs = Measure-Endpoint ("/dashboard/home?month=$month")
        reportMedianMs = Measure-Endpoint ("/reports/overview?month=$month")
        samplesPerEndpoint = $Runs
        balancesAndCategories = 'OK'
    }
} finally {
    if ($bootstrapped) {
        $null = Invoke-RestMethod -Method Delete -Uri "$apiEndpoint/users/me" -Headers $headers -ContentType application/json -Body '{"confirm":true}'
    } else {
        $null = Invoke-RestMethod -Method Post -Uri "$authEndpoint/accounts:delete?key=demo-key" -ContentType application/json -Body (@{ idToken = $created.idToken } | ConvertTo-Json)
    }
}
$result | ConvertTo-Json
