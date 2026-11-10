param([ValidateSet('debug','release')][string]$Variant='debug')
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mobile=Join-Path $workspace 'finor-mobile'
if($Variant -eq 'release' -and $env:EXPO_PUBLIC_API_URL -notmatch '^https://'){
 throw 'Defina EXPO_PUBLIC_API_URL com a URL HTTPS pública do backend antes de gerar o APK independente.'
}
$env:GRADLE_USER_HOME=Join-Path $workspace '.gradle'
Push-Location $mobile
try {
 & npx expo prebuild --platform android --no-install --no-clean
 if($LASTEXITCODE -ne 0){throw 'Falha ao gerar o projeto Android.'}
 Push-Location android
 try {
  $task=if($Variant -eq 'release'){':app:assembleRelease'}else{':app:assembleDebug'}
  & .\gradlew.bat $task --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
  if($LASTEXITCODE -ne 0){throw 'Falha na compilação Android. Consulte a saída do Gradle.'}
  $apk=Join-Path (Get-Location) "app/build/outputs/apk/$Variant/app-$Variant.apk"
  if(-not(Test-Path -LiteralPath $apk)){throw 'O Gradle não gerou o APK esperado.'}
  $output=Join-Path $workspace 'artifacts'
  New-Item -ItemType Directory -Force $output | Out-Null
  $target=Join-Path $output "finor-$Variant.apk"
  Copy-Item -LiteralPath $apk -Destination $target -Force
  Write-Output "APK: $target"
  Get-FileHash -LiteralPath $target -Algorithm SHA256
 } finally {Pop-Location}
} finally {Pop-Location}


