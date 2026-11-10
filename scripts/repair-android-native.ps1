param([switch]$Build)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mobile=Join-Path $workspace 'finor-mobile'
$sdkRoot=$env:ANDROID_HOME
if(-not $sdkRoot){$sdkRoot=$env:ANDROID_SDK_ROOT}
if(-not $sdkRoot){$sdkRoot=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$cmakeRoot=Join-Path $sdkRoot 'cmake/3.31.6/bin'
if(-not(Test-Path -LiteralPath (Join-Path $cmakeRoot 'cmake.exe'))){
 throw "Instale CMake 3.31.6 no Android Studio > SDK Manager > SDK Tools > Show Package Details > CMake. SDK: $sdkRoot. Depois execute este script novamente."
}
$ninjaVersion=(& (Join-Path $cmakeRoot 'ninja.exe') --version | Select-Object -First 1).Trim()
if($LASTEXITCODE -ne 0 -or [version]$ninjaVersion -lt [version]'1.12.0'){
 throw "Ninja $ninjaVersion incompatível. O pacote CMake precisa fornecer Ninja 1.12 ou superior."
}
Write-Output "CMake 3.31.6 e Ninja $ninjaVersion verificados."
$env:CMAKE_VERSION='3.31.6'
Push-Location $mobile
try {
 & npx expo prebuild --platform android --no-install --no-clean
 if($LASTEXITCODE -ne 0){throw 'Falha no prebuild.'}
 # Remove only generated native build directories; preserve dependencies and other caches.
 $relativeTargets=@('android/app/.cxx','android/app/build/intermediates/cxx',
 'node_modules/react-native-reanimated/android/.cxx','node_modules/react-native-reanimated/android/build/intermediates/cxx',
 'node_modules/react-native-worklets/android/.cxx','node_modules/react-native-worklets/android/build/intermediates/cxx')
 foreach($relative in $relativeTargets){
  $candidate=[IO.Path]::GetFullPath((Join-Path $mobile $relative))
  if(-not $candidate.StartsWith($mobile+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Diretório fora do projeto: $candidate"}
  if(Test-Path -LiteralPath $candidate){
   $resolved=(Resolve-Path -LiteralPath $candidate).Path
   if($resolved -ne $candidate){throw "Destino inesperado: $resolved"}
   Remove-Item -LiteralPath $resolved -Recurse -Force
  }
 }
 if($Build){
  & npx expo run:android --device
  if($LASTEXITCODE -ne 0){throw 'A compilação Android falhou. Consulte a seção What went wrong.'}
 } else {Write-Output 'Pronto. Execute: npx expo run:android --device'}
} finally {Pop-Location}
