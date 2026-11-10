param([switch]$Build)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar=Join-Path $workspace 'finor-api/target/finor-api-0.1.0.jar'
if(-not $Build -and -not(Test-Path -LiteralPath $jar)){throw 'Compile a API primeiro ou use -Build.'}
$listener=Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1
if($listener){
 $info=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
 if($info.Name -ne 'java.exe' -or $info.CommandLine -notmatch 'finor-api.*\.jar|com\.finor\.FinorApplication' -or $info.CommandLine -notmatch [regex]::Escape($workspace)){throw 'A porta 8080 pertence a outro aplicativo. Nenhum processo foi encerrado.'}
 Stop-Process -Id $listener.OwningProcess
}
if($Build){
 $buildLog=Join-Path $workspace 'local-api-package.log'
 & mvn -B "-Dmaven.repo.local=$workspace/.m2" -f (Join-Path $workspace 'finor-api/pom.xml') -DskipTests package *> $buildLog
 if($LASTEXITCODE -ne 0){throw "Falha ao compilar. Consulte $buildLog"}
}
$env:SPRING_PROFILES_ACTIVE='firestore'
$env:FIREBASE_PROJECT_ID='demo-finor'
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8085'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
$env:SERVER_PORT='8080'
$env:JWT_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
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
$process=Start-Process -FilePath java -ArgumentList @('-jar',$jar) -WorkingDirectory $workspace -WindowStyle Hidden -RedirectStandardOutput (Join-Path $workspace 'api-local.out.log') -RedirectStandardError (Join-Path $workspace 'api-local.err.log') -PassThru
"API local iniciada em segundo plano: PID $($process.Id)."
