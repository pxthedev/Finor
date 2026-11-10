$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$apk=Join-Path $root 'artifacts/android/finor.apk'
$sdk=$env:ANDROID_HOME
if(-not $sdk){$sdk=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$tools=Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$signer=Join-Path $tools.FullName 'apksigner.bat'
$cert=(& $signer verify --print-certs $apk 2>&1|Out-String)
if($LASTEXITCODE -ne 0){throw 'A assinatura do APK não passou na verificação.'}
$sha1=[regex]::Match($cert,'certificate SHA-1 digest:\s*([A-Fa-f0-9]+)').Groups[1].Value
if($sha1.Length -ne 40){throw 'SHA-1 do certificado não encontrado.'}
$aapt=Join-Path $tools.FullName 'aapt.exe'
$badging=(& $aapt dump badging $apk 2>&1|Out-String)
if($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='com.finor.app'"){throw 'Package Android incorreto.'}
if($badging -match 'application-debuggable'){throw 'O APK ainda está em modo debug.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($apk)
try {
  $stream=$zip.GetEntry('assets/index.android.bundle').Open()
  $memory=[IO.MemoryStream]::new()
  try {$stream.CopyTo($memory);$bundle=[Text.Encoding]::UTF8.GetString($memory.ToArray())} finally {$stream.Dispose();$memory.Dispose()}
  $google=Get-Content (Join-Path $root 'artifacts/google-login-config.json') -Raw|ConvertFrom-Json
  if(-not $bundle.Contains($google.WebClientId) -or -not $bundle.Contains('finor.discloud.dev')){throw 'Configuração Google/API ausente do APK.'}
  if($bundle -match 'gsk_[A-Za-z0-9]{30,}' -or $bundle.Contains('BEGIN PRIVATE KEY')){throw 'Credencial privada indevida no bundle.'}
} finally {$zip.Dispose()}
$info=@{PackageName='com.finor.app';Sha1=$sha1;SizeMB=[math]::Round((Get-Item $apk).Length/1MB,1);Sha256=(Get-FileHash $apk -Algorithm SHA256).Hash}
$info|ConvertTo-Json|Set-Content (Join-Path $root 'artifacts/android/apk-info.json')
'APK validado: assinatura, modo release, Google e API HTTPS.'
$info|ConvertTo-Json
