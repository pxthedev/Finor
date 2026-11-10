param([string]$JarPath=(Join-Path $PSScriptRoot '..\.tools\firebase-emulators\cloud-firestore-emulator-v1.22.0.jar'))
$ErrorActionPreference='Stop'
if(-not(Test-Path -LiteralPath $JarPath)){throw 'Baixe o emulador oficial com Firebase CLI ou informe -JarPath para cloud-firestore-emulator-v1.22.0.jar.'}
$actual=(Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$expected='9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c'
if($actual -ne $expected){throw 'O SHA-256 do emulador não corresponde ao artefato oficial esperado.'}
$rules=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\firestore.rules'))
& java -jar $JarPath --host 127.0.0.1 --port 8085 --project_id demo-finor --rules $rules --single_project_mode true
if($LASTEXITCODE -ne 0){throw 'O emulador encerrou com erro.'}
