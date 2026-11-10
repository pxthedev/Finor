param([switch]$StartBuild)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$mobile=Join-Path $root 'finor-mobile'
$config=Get-Content (Join-Path $root 'artifacts/google-login-config.json') -Raw|ConvertFrom-Json
$variables=[ordered]@{EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=$config.WebClientId;EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID=$config.IosClientId}
foreach($value in $variables.Values){if($value -notmatch '^\d+-[A-Za-z0-9-]+\.apps\.googleusercontent\.com$'){throw 'ID OAuth inválido.'}}
if($config.BundleId -ne 'com.finor.app'){throw 'Bundle ID iOS incorreto.'}
$cli=Get-ChildItem (Join-Path $env:LOCALAPPDATA 'npm-cache/_npx') -Directory | ForEach-Object {$p=Join-Path $_.FullName 'node_modules/eas-cli/bin/run';if(Test-Path $p){$p}} | Select-Object -First 1
if(-not $cli){throw 'EAS CLI não encontrado.'}
$env:EAS_NO_VCS='1'
$env:EAS_PROJECT_ROOT=$mobile
Push-Location $mobile
try {
  foreach($variable in $variables.GetEnumerator()){
    & node $cli env:set preview --name $variable.Key --value $variable.Value --visibility plaintext --scope project --non-interactive
    if($LASTEXITCODE -ne 0){throw "Falha ao configurar $($variable.Key)."}
  }
  $envPath=Join-Path $mobile '.env'
  $content=if(Test-Path $envPath){[IO.File]::ReadAllText($envPath)}else{''}
  foreach($variable in $variables.GetEnumerator()){
    $line="$($variable.Key)=$($variable.Value)"
    $pattern="(?m)^$($variable.Key)=.*$"
    if($content -match $pattern){$content=[regex]::Replace($content,$pattern,$line)}else{$content=$content.TrimEnd()+"`n$line`n"}
  }
  [IO.File]::WriteAllText($envPath,$content,[Text.UTF8Encoding]::new($false))
} finally {Pop-Location}
if($StartBuild){& (Join-Path $PSScriptRoot 'eas-ios-build.ps1') -StartBuild}
