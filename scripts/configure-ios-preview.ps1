param(
    [Parameter(Mandatory)][string]$ApiUrl,
    [switch]$StartBuild
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mobile = Join-Path $workspace 'finor-mobile'
$uri = $null
if (-not [uri]::TryCreate($ApiUrl, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.Host -in @('localhost', '127.0.0.1', '::1', '0.0.0.0') -or $uri.Host.EndsWith('.invalid') -or $uri.Query -or $uri.Fragment) {
    throw 'Informe a URL HTTPS real da API, incluindo /api/v1.'
}
$baseUrl = $uri.AbsoluteUri.TrimEnd('/')
if (-not $uri.AbsolutePath.TrimEnd('/').EndsWith('/api/v1')) { throw 'A URL da API precisa terminar com /api/v1.' }
$config = Get-Content (Join-Path $mobile 'app.json') -Raw | ConvertFrom-Json
if ($config.expo.extra.eas.projectId -ne 'e40941cd-113c-46e9-ac62-869cf2fcd3f1' -or $config.expo.owner -ne 'voltarellidev') {
    throw 'O projeto Expo não corresponde ao Finor verificado. Confira app.json antes de atualizar as variáveis.'
}
# An unauthenticated financial endpoint must answer 401, not a public data response.
$response = Invoke-WebRequest -Uri "$baseUrl/users/me" -SkipHttpErrorCheck -TimeoutSec 20
if ($response.StatusCode -ne 401) { throw "A API não apresentou o comportamento esperado (HTTP $($response.StatusCode)). Nenhuma variável foi alterada." }
Push-Location $mobile
try {
    $env:EAS_PROJECT_ROOT = $mobile
    $null = & git rev-parse --show-toplevel 2>$null
    if ($LASTEXITCODE -ne 0) { $env:EAS_NO_VCS = '1' }
    # Only these public app variables are sent. Backend keys and .env are never uploaded.
    $variables = [ordered]@{
        EXPO_PUBLIC_API_URL = $baseUrl
        FINOR_ALLOW_LOCAL_NETWORK = '0'
    }
    if ($env:EXPO_PUBLIC_FIREBASE_API_KEY -match '^AIza[0-9A-Za-z_-]{35}$') {
        $variables['EXPO_PUBLIC_FIREBASE_API_KEY'] = $env:EXPO_PUBLIC_FIREBASE_API_KEY
    }
    foreach ($variable in $variables.GetEnumerator()) {
        & npx --yes eas-cli@latest env:set preview --name $variable.Key --value $variable.Value --visibility plaintext --scope project --non-interactive
        if ($LASTEXITCODE -ne 0) { throw "Falha ao configurar $($variable.Key) no projeto Expo." }
    }
    "Ambiente preview configurado para $baseUrl."
    if ($StartBuild) {
        & npx --yes eas-cli@latest build --platform ios --profile ios-unsigned --non-interactive --no-wait
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao solicitar o build do IPA sem assinatura.' }
        'Build solicitado. O IPA fica disponível após a compilação terminar com sucesso e precisa ser assinado antes da instalação.'
    }
} finally { Pop-Location }
