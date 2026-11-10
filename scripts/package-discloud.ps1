param(
    [string]$ServiceAccountPath,
    [switch]$SkipBuild
)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$api=Join-Path $workspace 'finor-api'
$templates=Join-Path $api 'deploy/discloud'
$credential=$null
if(-not $ServiceAccountPath){
    $candidate=Join-Path $workspace '.secrets/firebase-admin.json'
    if(Test-Path -LiteralPath $candidate){$ServiceAccountPath=$candidate}
}
if($ServiceAccountPath){
    $ServiceAccountPath=(Resolve-Path -LiteralPath $ServiceAccountPath).Path
    $credential=Get-Content -LiteralPath $ServiceAccountPath -Raw | ConvertFrom-Json
    if($credential.type -ne 'service_account' -or $credential.project_id -ne 'finance-app-7ebf6' -or -not $credential.private_key -or -not $credential.client_email){
        throw 'A credencial deve ser uma conta de serviço do projeto Firebase finance-app-7ebf6.'
    }
}
if(-not $SkipBuild){
    $log=Join-Path $workspace 'discloud-package-build.log'
    & mvn -B "-Dmaven.repo.local=$workspace/.m2" -f (Join-Path $api 'pom.xml') -Pdiscloud -DskipTests package *> $log
    if($LASTEXITCODE -ne 0){throw "Falha ao compilar a API. Consulte $log"}
}
$jar=Join-Path $api 'target-discloud/finor-api-0.1.0.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Compile a API antes de usar -SkipBuild.'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$output=Join-Path $workspace "artifacts/discloud/$stamp"
New-Item -ItemType Directory -Path $output -Force | Out-Null
$utf8=[Text.UTF8Encoding]::new($false)
foreach($name in @('discloud.config','start.sh','.env.example','LEIA-ME.txt')){
    $content=[IO.File]::ReadAllText((Join-Path $templates $name)).Replace("`r`n","`n")
    [IO.File]::WriteAllText((Join-Path $output $name),$content,$utf8)
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $output 'app.jar')
if($credential){
    $directory=Join-Path $output 'credentials'
    New-Item -ItemType Directory -Path $directory | Out-Null
    Copy-Item -LiteralPath $ServiceAccountPath -Destination (Join-Path $directory 'firebase-admin.json')
    $ai=@{}
    foreach($line in Get-Content -LiteralPath (Join-Path $workspace '.env')){
        if($line -match '^\s*(GROQ_API_KEY|GROQ_MODEL|GROQ_CHAT_COMPLETIONS_URL)\s*=\s*(.*)$'){
            $key=$Matches[1];$value=$Matches[2].Trim()
            if($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))){$value=$value.Substring(1,$value.Length-2)}
            $ai[$key]=$value
        }
    }
    $values=[ordered]@{
        FIREBASE_PROJECT_ID='finance-app-7ebf6'
        FIREBASE_DATABASE_ID='(default)'
        GOOGLE_APPLICATION_CREDENTIALS='./credentials/firebase-admin.json'
        GROQ_API_KEY=$ai['GROQ_API_KEY']
        GROQ_MODEL=$(if($ai['GROQ_MODEL']){$ai['GROQ_MODEL']}else{'openai/gpt-oss-20b'})
        GROQ_CHAT_COMPLETIONS_URL='https://api.groq.com/openai/v1/chat/completions'
    }
    $lines=foreach($entry in $values.GetEnumerator()){
        $value=[string]$entry.Value
        if($value.Contains("`n") -or $value.Contains("`r")){throw 'Variável de produção inválida.'}
        $escaped=$value.Replace("'", "'\''")
        "$($entry.Key)='$escaped'"
    }
    [IO.File]::WriteAllText((Join-Path $output '.env'),(($lines -join "`n")+"`n"),$utf8)
}
# Explicit entries include .env when present; Compress-Archive can skip hidden files.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=Join-Path (Split-Path -Parent $output) "finor-discloud-$stamp.zip"
[IO.Compression.ZipFile]::CreateFromDirectory($output,$zip,[IO.Compression.CompressionLevel]::Optimal,$false)
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{
    $names=@($archive.Entries.FullName)
    foreach($required in @('discloud.config','start.sh','app.jar')){
        if($required -notin $names){throw "Arquivo ausente no ZIP: $required"}
    }
    if($credential -and ('.env' -notin $names -or 'credentials/firebase-admin.json' -notin $names)){throw 'Pacote privado incompleto.'}
}finally{$archive.Dispose()}
[pscustomobject]@{Package=$zip;Directory=$output;CredentialsIncluded=!!$credential;ProductionConfigurationReady=!!$credential;ApiUrl='https://finor.discloud.dev/api/v1'} | ConvertTo-Json
