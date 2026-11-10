param([string]$BuildId,[switch]$StartBuild,[switch]$Watch)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$mobile=Join-Path $root 'finor-mobile'
$cli=Get-ChildItem (Join-Path $env:LOCALAPPDATA 'npm-cache/_npx') -Directory | ForEach-Object { $candidate=Join-Path $_.FullName 'node_modules/eas-cli/bin/run'; if(Test-Path $candidate){$candidate} } | Select-Object -First 1
if(-not $cli){throw 'EAS CLI não encontrado no cache npm.'}
$env:EAS_NO_VCS='1'
$env:EAS_PROJECT_ROOT=$mobile
Push-Location $mobile
try {
  if($StartBuild){
    & node $cli build --platform ios --profile ios-unsigned --non-interactive --no-wait
    if($LASTEXITCODE -ne 0){throw 'Falha ao solicitar build iOS.'}
  } else {
    if(-not $BuildId){throw 'Informe -BuildId ou -StartBuild.'}
    do {
    $raw=(& node $cli build:view $BuildId --json | Out-String)
    if($LASTEXITCODE -ne 0){throw 'Falha ao consultar build iOS.'}
    $build=$raw.Substring($raw.IndexOf('{'))|ConvertFrom-Json
    $raw | Set-Content (Join-Path $root 'artifacts/ios-build-status.json')
    [pscustomobject]@{Id=$build.id;Status=$build.status;Artifacts=$build.artifacts;Error=$build.error}|ConvertTo-Json -Depth 5
    if($Watch -and $build.status -notin @('FINISHED','ERRORED','CANCELED')){Start-Sleep -Seconds 30}
    } while($Watch -and $build.status -notin @('FINISHED','ERRORED','CANCELED'))
    if($build.status -eq 'FINISHED' -and $build.artifacts.applicationArchiveUrl){
      $output=Join-Path $root 'artifacts/ios'
      New-Item -ItemType Directory -Path $output -Force | Out-Null
      $ipa=Join-Path $output 'finor-unsigned.ipa'
      Invoke-WebRequest $build.artifacts.applicationArchiveUrl -OutFile $ipa -TimeoutSec 180
      Add-Type -AssemblyName System.IO.Compression.FileSystem
      $archive=[IO.Compression.ZipFile]::OpenRead($ipa)
      try {
        foreach($required in @('Payload/finor.app/Info.plist','Payload/finor.app/main.jsbundle')){
          if(-not $archive.GetEntry($required)){throw "IPA sem o arquivo obrigatório $required"}
        }
      } finally {$archive.Dispose()}
      "IPA conferido e salvo em $ipa"
      Get-FileHash -LiteralPath $ipa -Algorithm SHA256 | Select-Object Algorithm,Hash
    }
    if($build.status -eq 'ERRORED'){
      $index=0
      foreach($url in $build.logFiles){
        $log=Join-Path $root "artifacts/ios-build-log-$index.txt"
        Invoke-WebRequest $url -OutFile $log -TimeoutSec 30
        Get-Content $log -Tail 35 | ForEach-Object {
          try { $entry=$_ | ConvertFrom-Json; if($entry.msg){$entry.msg} } catch { $_ }
        }
        $index++
      }
    }
  }
} finally {Pop-Location}
