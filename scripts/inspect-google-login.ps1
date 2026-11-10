param([switch]$RegisterIos,[switch]$RegisterAndroid,[string]$Sha1)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$account=Get-Content (Join-Path $root '.secrets/firebase-admin.json') -Raw | ConvertFrom-Json
function Base64Url([byte[]]$bytes){[Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')}
$now=[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header=Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"RS256","typ":"JWT"}'))
$claims=@{iss=$account.client_email;scope='https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/firebase';aud='https://oauth2.googleapis.com/token';iat=$now;exp=$now+3600}|ConvertTo-Json -Compress
$payload=Base64Url ([Text.Encoding]::UTF8.GetBytes($claims))
$rsa=[Security.Cryptography.RSA]::Create()
try {$rsa.ImportFromPem($account.private_key);$signature=Base64Url ($rsa.SignData([Text.Encoding]::UTF8.GetBytes("$header.$payload"),[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1))} finally {$rsa.Dispose()}
$auth=Invoke-RestMethod https://oauth2.googleapis.com/token -Method Post -Body @{grant_type='urn:ietf:params:oauth:grant-type:jwt-bearer';assertion="$header.$payload.$signature"} -TimeoutSec 30
$headers=@{Authorization="Bearer $($auth.access_token)"}
$project=$account.project_id
$provider=Invoke-WebRequest "https://identitytoolkit.googleapis.com/admin/v2/projects/$project/defaultSupportedIdpConfigs/google.com" -Headers $headers -SkipHttpErrorCheck -TimeoutSec 30
"Google provider HTTP $($provider.StatusCode)"
if($provider.StatusCode -eq 200){$p=$provider.Content|ConvertFrom-Json; [pscustomobject]@{Enabled=$p.enabled;WebClientId=$p.clientId}|ConvertTo-Json}
foreach($platform in @('iosApps','androidApps')){
  $response=Invoke-WebRequest "https://firebase.googleapis.com/v1beta1/projects/$project/$platform" -Headers $headers -SkipHttpErrorCheck -TimeoutSec 30
  "$platform HTTP $($response.StatusCode)"
  if($response.StatusCode -eq 200){
    $apps=($response.Content|ConvertFrom-Json).apps
    $register=($RegisterIos -and $platform -eq 'iosApps') -or ($RegisterAndroid -and $platform -eq 'androidApps')
    $registered=$apps | Where-Object {$_.bundleId -eq 'com.finor.app' -or $_.packageName -eq 'com.finor.app'}
    if($register -and -not $registered){
      $appBody=if($platform -eq 'iosApps'){'{"bundleId":"com.finor.app","displayName":"finor"}'}else{'{"packageName":"com.finor.app","displayName":"finor"}'}
      $operation=Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/projects/$project/$platform" -Method Post -Headers $headers -ContentType application/json -Body $appBody -TimeoutSec 30
      for($attempt=0;$attempt -lt 12 -and -not $operation.done;$attempt++){
        Start-Sleep -Seconds 2
        $operation=Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/$($operation.name)" -Headers $headers -TimeoutSec 30
      }
      if($operation.error){throw "Não foi possível registrar $platform : $($operation.error.message)"}
      $apps=(Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/projects/$project/$platform" -Headers $headers -TimeoutSec 30).apps
    }
    $apps|Select-Object name,appId,bundleId,packageName,displayName|ConvertTo-Json
    foreach($app in $apps){
      if($app.packageName -eq 'com.finor.app' -and $Sha1){
        $hash=$Sha1.Replace(':','').ToLowerInvariant()
        if($hash -notmatch '^[a-f0-9]{40}$'){throw 'SHA-1 inválido.'}
        $certs=(Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/$($app.name)/sha" -Headers $headers -TimeoutSec 30).certificates
        if(-not ($certs | Where-Object {$_.shaHash -and $_.shaHash.Replace(':','').ToLowerInvariant() -eq $hash})){
          $null=Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/$($app.name)/sha" -Method Post -Headers $headers -ContentType application/json -Body (@{shaHash=$hash;certType='SHA_1'}|ConvertTo-Json) -TimeoutSec 30
        }
        'Certificado SHA-1 do APK registrado no Firebase.'
        for($attempt=0;$attempt -lt 8;$attempt++){
          $c=Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/$($app.name)/config" -Headers $headers -TimeoutSec 30
          $native=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($c.configFileContents))|ConvertFrom-Json
          $oauth=$native.client.oauth_client | Where-Object {$_.client_type -eq 1 -and $_.android_info.package_name -eq 'com.finor.app' -and $_.android_info.certificate_hash -and $_.android_info.certificate_hash.ToLowerInvariant() -eq $hash} | Select-Object -First 1
          if($oauth){break}
          Start-Sleep -Seconds 2
        }
        if(-not $oauth){throw 'O cliente OAuth Android ainda não foi gerado para esse certificado.'}
        @{PackageName='com.finor.app';Sha1=$hash;AndroidClientId=$oauth.client_id;WebClientId=$p.clientId}|ConvertTo-Json|Set-Content (Join-Path $root 'artifacts/google-android-config.json')
        'Cliente OAuth Android correspondente ao APK: confirmado.'
      }
      if($app.bundleId -eq 'com.finor.app'){
        $c=Invoke-RestMethod "https://firebase.googleapis.com/v1beta1/$($app.name)/config" -Headers $headers -TimeoutSec 30
        $plist=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($c.configFileContents))
        [xml]$xml=$plist
        $nodes=$xml.plist.dict.ChildNodes
        $iosClient=$null
        for($i=0;$i -lt $nodes.Count-1;$i++){if($nodes[$i].InnerText -in @('CLIENT_ID','REVERSED_CLIENT_ID')){"$($nodes[$i].InnerText): $($nodes[$i+1].InnerText)"};if($nodes[$i].InnerText -eq 'CLIENT_ID'){$iosClient=$nodes[$i+1].InnerText}}
        if($iosClient -and $p.clientId){@{WebClientId=$p.clientId;IosClientId=$iosClient;BundleId=$app.bundleId}|ConvertTo-Json|Set-Content (Join-Path $root 'artifacts/google-login-config.json')}
      }
    }
  }
}
