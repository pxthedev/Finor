param([Parameter(Mandatory)][string]$Package)
$ErrorActionPreference='Stop'
$packagePath=(Resolve-Path -LiteralPath $Package).Path
$storePath=Join-Path $env:USERPROFILE '.discloud/.cli'
$store=Get-Content -LiteralPath $storePath -Raw
try {$config=$store | ConvertFrom-Json} catch {$config=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($store)) | ConvertFrom-Json}
if(-not $config.token){throw 'Faça login no CLI Discloud primeiro.'}
$responsePath=Join-Path (Split-Path $packagePath) 'upload-response.json'
# Supply the credential through stdin, keeping it out of arguments and logs.
$curlConfig='header = "api-token: '+$config.token+'"'
$curlConfig | & curl.exe --config - --http1.1 --connect-timeout 30 --max-time 600 --silent --show-error --output $responsePath --write-out 'HTTP %{http_code}\n' --form "file.zip=@$packagePath;type=application/zip" https://api.discloud.app/v2/upload
if($LASTEXITCODE -ne 0){throw 'A transferência para a Discloud foi interrompida.'}
try {$result=Get-Content -LiteralPath $responsePath -Raw | ConvertFrom-Json} catch {throw 'A Discloud não retornou JSON. Consulte o código HTTP acima.'}
[pscustomobject]@{Status=$result.status;Message=$result.message;AppId=$result.app.id} | ConvertTo-Json
if($result.status -ne 'ok'){throw 'A Discloud recusou o pacote.'}
