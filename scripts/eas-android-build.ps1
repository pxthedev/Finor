param([string]$BuildId,[switch]$StartBuild,[switch]$Watch)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$mobile=Join-Path $root 'finor-mobile'
$cli=Get-ChildItem (Join-Path $env:LOCALAPPDATA 'npm-cache/_npx') -Directory | ForEach-Object {$p=Join-Path $_.FullName 'node_modules/eas-cli/bin/run';if(Test-Path $p){$p}} | Select-Object -First 1
if(-not $cli){throw 'EAS CLI não encontrado.'}
$env:EAS_NO_VCS='1'
$env:EAS_PROJECT_ROOT=$mobile
Push-Location $mobile
try {
  if($StartBuild){
    & node $cli build --platform android --profile preview --no-wait
    if($LASTEXITCODE -ne 0){throw 'Falha ao solicitar APK.'}
  } else {
    if(-not $BuildId){
      $latestRaw=(& node $cli build:list --platform android --limit 1 --json --non-interactive|Out-String)
      if($LASTEXITCODE -ne 0){throw 'Falha ao localizar build Android.'}
      $latest=$latestRaw.Substring($latestRaw.IndexOf('['))|ConvertFrom-Json
      $BuildId=@($latest)[0].id
      if(-not $BuildId){throw 'Nenhum build Android encontrado.'}
    }
    do {
      $raw=(& node $cli build:view $BuildId --json|Out-String)
      if($LASTEXITCODE -ne 0){throw 'Falha ao consultar build Android.'}
      $build=$raw.Substring($raw.IndexOf('{'))|ConvertFrom-Json
      $raw|Set-Content (Join-Path $root 'artifacts/android-build-status.json')
      [pscustomobject]@{Id=$build.id;Status=$build.status;Artifacts=$build.artifacts;Error=$build.error}|ConvertTo-Json -Depth 5
      if($Watch -and $build.status -notin @('FINISHED','ERRORED','CANCELED')){Start-Sleep -Seconds 30}
    } while($Watch -and $build.status -notin @('FINISHED','ERRORED','CANCELED'))
    if($build.status -eq 'FINISHED'){
      $url=$build.artifacts.applicationArchiveUrl
      if(-not $url){$url=$build.artifacts.buildUrl}
      if(-not $url){throw 'APK não disponível.'}
      $output=Join-Path $root 'artifacts/android'
      New-Item -ItemType Directory -Path $output -Force|Out-Null
      $apk=Join-Path $output 'finor.apk'
      Invoke-WebRequest $url -OutFile $apk -TimeoutSec 300
      Add-Type -AssemblyName System.IO.Compression.FileSystem
      $zip=[IO.Compression.ZipFile]::OpenRead($apk)
      try {
        foreach($entry in @('AndroidManifest.xml','assets/index.android.bundle')){if(-not $zip.GetEntry($entry)){throw "APK sem $entry"}}
        $stream=$zip.GetEntry('assets/index.android.bundle').Open()
        $memory=[IO.MemoryStream]::new()
        try {$stream.CopyTo($memory);$bundle=[Text.Encoding]::UTF8.GetString($memory.ToArray())} finally {$stream.Dispose();$memory.Dispose()}
        if(-not $bundle.Contains('finor.discloud.dev')){throw 'API pública ausente do APK.'}
      } finally {$zip.Dispose()}
      "APK conferido e salvo em $apk"
    }
    if($build.status -eq 'ERRORED'){
      $i=0
      foreach($url in $build.logFiles){
        $log=Join-Path $root "artifacts/android-build-log-$i.txt"
        Invoke-WebRequest $url -OutFile $log -TimeoutSec 30
        Get-Content $log -Tail 40 | ForEach-Object {try{($_|ConvertFrom-Json).msg}catch{$_}}
        $i++
      }
    }
  }
} finally {Pop-Location}
