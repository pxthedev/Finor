param([switch]$Lan)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:XDG_CONFIG_HOME=Join-Path $workspace '.tools/firebase-config'
$env:CI='1'
$firebaseArguments=@('emulators:start','--only','auth','--project','demo-finor')
if($Lan){
 $configDirectory=Join-Path $workspace '.tools/firebase-config'
 New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
 $lanConfig=Join-Path $configDirectory 'auth-lan.json'
 @{emulators=@{auth=@{host='0.0.0.0';port=9099};ui=@{enabled=$false};singleProjectMode=$true}} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $lanConfig -Encoding utf8
 $firebaseArguments+=@('--config',$lanConfig)
}
Push-Location $workspace
try {
 $cli=Join-Path $workspace '.tools/firebase-cli/node_modules/firebase-tools/lib/bin/firebase.js'
 if(Test-Path -LiteralPath $cli){& node $cli @firebaseArguments}
 else {& npx --yes firebase-tools @firebaseArguments}
 if($LASTEXITCODE -ne 0){throw 'Falha no emulador Authentication.'}
} finally {Pop-Location}
