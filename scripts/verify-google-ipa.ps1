$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$config=Get-Content (Join-Path $root 'artifacts/google-login-config.json') -Raw|ConvertFrom-Json
$ipa=Join-Path $root 'artifacts/ios/finor-unsigned.ipa'
$plist=Join-Path $root 'artifacts/ios/Info.plist'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($ipa)
try {
  [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('Payload/finor.app/Info.plist'),$plist,$true)
  $stream=$zip.GetEntry('Payload/finor.app/main.jsbundle').Open()
  $memory=[IO.MemoryStream]::new()
  try {$stream.CopyTo($memory);$bundle=[Text.Encoding]::UTF8.GetString($memory.ToArray())} finally {$stream.Dispose();$memory.Dispose()}
  foreach($required in @($config.WebClientId,$config.IosClientId,'finor.discloud.dev')){
    if(-not $bundle.Contains($required)){throw 'O bundle iOS não contém toda a configuração Google/API esperada.'}
  }
} finally {$zip.Dispose()}
$parser=Join-Path $root 'finor-mobile/node_modules/bplist-parser'
& node -e 'const fs=require("fs");const p=require(process.argv[1]).parseBuffer(fs.readFileSync(process.argv[2]))[0];const scheme=process.argv[3].split(".").reverse().join(".");if(p.CFBundleIdentifier!=="com.finor.app")throw Error("Bundle ID incorreto");if(!(p.CFBundleURLTypes||[]).some(x=>(x.CFBundleURLSchemes||[]).includes(scheme)))throw Error("Callback Google ausente");console.log("IPA validado: Bundle ID, IDs Google, callback iOS e API HTTPS corretos.");' $parser $plist $config.IosClientId
if($LASTEXITCODE -ne 0){throw 'Falha na validação do IPA Google.'}
