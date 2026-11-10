param([switch]$Tests)
$ErrorActionPreference='Stop'
$workspace=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:SPRING_PROFILES_ACTIVE='firestore'
$env:FIREBASE_PROJECT_ID='demo-finor'
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8085'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
$env:JWT_SECRET=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
# Only AI configuration is loaded; database and authentication stay on local emulators.
if (-not $Tests) {
 $envFile=Join-Path $workspace '.env'
 if(Test-Path -LiteralPath $envFile){
  foreach($line in Get-Content -LiteralPath $envFile){
   if($line -match '^\s*(GROQ_API_KEY|GROQ_MODEL|GROQ_CHAT_COMPLETIONS_URL)\s*=\s*(.*)$'){
    $name=$Matches[1];$value=$Matches[2].Trim()
    if($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))){$value=$value.Substring(1,$value.Length-2)}
    if($value){[Environment]::SetEnvironmentVariable($name,$value,'Process')}
   }
  }
 }
}
Push-Location $workspace
try{
 if($Tests){$env:FINOR_FIRESTORE_TESTS='1'; & mvn -B "-Dmaven.repo.local=$workspace/.m2" -f finor-api/pom.xml test; if($LASTEXITCODE -ne 0){throw 'Falha nos testes Firestore.'}}
 else{& mvn -B "-Dmaven.repo.local=$workspace/.m2" -f finor-api/pom.xml spring-boot:run; if($LASTEXITCODE -ne 0){throw 'Falha ao executar a API.'}}
}finally{Pop-Location}


