param(
    [ValidateSet('All','AI','SMTP','Database')] [string]$Only='All',
    [string]$EnvFile=(Join-Path $PSScriptRoot '..\.env')
)
$ErrorActionPreference='Stop'
if(-not(Test-Path -LiteralPath $EnvFile)){throw 'Arquivo .env não encontrado. Copie .env.example e configure suas credenciais.'}
$variables=@{}
$number=0
foreach($line in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile))){
    $number++;$entry=$line.Trim()
    if(-not $entry -or $entry.StartsWith('#')){continue}
    if($entry -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$'){throw "Formato inválido na linha $number do arquivo de ambiente."}
    $name=$Matches[1];$value=$Matches[2].Trim()
    if($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))){$value=$value.Substring(1,$value.Length-2)}
    $variables[$name]=$value
}
$required=@()
if($Only -in @('All','Database')){$required+=@('FIREBASE_PROJECT_ID')}
if($Only -in @('All','AI')){$required+=@('GROQ_API_KEY')}
if($Only -in @('All','SMTP')){$required+=@('SPRING_MAIL_HOST','SPRING_MAIL_USERNAME','SPRING_MAIL_PASSWORD','MAIL_FROM','SMTP_TEST_TO')}
$missing=@($required|Where-Object {(-not $variables.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($variables[$_])) -and [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_,'Process'))})
if($missing.Count){throw ('Validação real não executada. Preencha: '+($missing -join ', '))}
# Carregamento literal: não executa conteúdo do .env e não imprime seus valores.
$previous=@{}
$allowed=@('GROQ_API_KEY','GROQ_MODEL','GROQ_CHAT_COMPLETIONS_URL','FIREBASE_PROJECT_ID','FIREBASE_DATABASE_ID','GOOGLE_APPLICATION_CREDENTIALS','MAIL_FROM','SMTP_TEST_TO','SPRING_MAIL_HOST','SPRING_MAIL_PORT','SPRING_MAIL_USERNAME','SPRING_MAIL_PASSWORD','SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE','SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE')
foreach($name in $variables.Keys){if($name -in $allowed -and $variables[$name]){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$variables[$name],'Process')}}
$previousFlag=$env:FINOR_LIVE_INTEGRATIONS
try{
    $env:FINOR_LIVE_INTEGRATIONS='1'
    $test=switch($Only){'AI'{'LiveIntegrationsTest#aiReturnsStructuredFinancialIntent'}'SMTP'{'LiveIntegrationsTest#smtpAuthenticatesAndSends'}'Database'{'LiveIntegrationsTest#firestoreConnectsWithCredentials'}default{'LiveIntegrationsTest'}}
    $apiDir=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\finor-api'))
    Push-Location $apiDir
    try { & mvn -B "-Dtest=$test" test; if($LASTEXITCODE -ne 0){throw 'A validação real falhou. Veja os resultados sem credenciais nos relatórios Maven.'} }
    finally { Pop-Location }
} finally {
    $env:FINOR_LIVE_INTEGRATIONS=$previousFlag
    foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}



