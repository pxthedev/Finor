param([string]$ApiUrl='https://finor.discloud.dev/api/v1')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$source=Get-Content (Join-Path $root 'finor-mobile/src/lib/firebase.ts') -Raw
$key=[regex]::Match($source,'apiKey:\s*"([^"]+)"').Groups[1].Value
if(-not $key){throw 'Chave pública Firebase ausente.'}
$token=$null
$bootstrapped=$false
try {
  $body=@{email=('finor-deploy-check-'+[guid]::NewGuid().ToString('N')+'@example.com');password=([guid]::NewGuid().ToString('N')+'!aA8');returnSecureToken=$true}|ConvertTo-Json
  $auth=Invoke-WebRequest "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key" -Method Post -ContentType application/json -Body $body -SkipHttpErrorCheck -TimeoutSec 30
  if($auth.StatusCode -ne 200){$errorCode=($auth.Content|ConvertFrom-Json).error.message;throw "Firebase cadastro: HTTP $($auth.StatusCode), $errorCode"}
  $token=($auth.Content|ConvertFrom-Json).idToken
  $headers=@{Authorization="Bearer $token"}
  $result=Invoke-WebRequest "$ApiUrl/auth/bootstrap" -Method Post -Headers $headers -ContentType application/json -Body '{}' -SkipHttpErrorCheck -TimeoutSec 60
  if($result.StatusCode -ne 200){throw "API bootstrap: HTTP $($result.StatusCode). Consulte os logs do servidor."}
  $bootstrapped=$true
  $profile=Invoke-WebRequest "$ApiUrl/users/me" -Headers $headers -SkipHttpErrorCheck -TimeoutSec 30
  if($profile.StatusCode -ne 200){throw "API perfil: HTTP $($profile.StatusCode)."}
  'Firebase cadastro, autenticação, Firestore e leitura do perfil: OK.'
} finally {
  if($token){
    if($bootstrapped){
      $cleanup=Invoke-WebRequest "$ApiUrl/users/me" -Method Delete -Headers @{Authorization="Bearer $token"} -ContentType application/json -Body '{"confirm":true}' -SkipHttpErrorCheck -TimeoutSec 60
      if($cleanup.StatusCode -notin @(200,204)){throw "Falha ao remover a conta temporária: HTTP $($cleanup.StatusCode)."}
      'Conta temporária removida.'
    } else {
      $cleanup=Invoke-WebRequest "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$key" -Method Post -ContentType application/json -Body (@{idToken=$token}|ConvertTo-Json) -SkipHttpErrorCheck -TimeoutSec 30
      if($cleanup.StatusCode -ne 200){throw 'Falha ao remover o usuário temporário do Firebase.'}
      'Usuário temporário removido do Firebase.'
    }
  }
}
