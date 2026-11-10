$ErrorActionPreference='Stop'
$base='http://127.0.0.1:18089/api/v1'
function Post($path,$body,$headers=@{}) { Invoke-RestMethod -Uri ($base+$path) -Method Post -Headers $headers -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 10) }
$suffix=[guid]::NewGuid().ToString()
$password=[guid]::NewGuid().ToString()+'A!'
$first=Post '/auth/register' @{name='Teste A';email="a-$suffix@example.test";password=$password}
$second=Post '/auth/register' @{name='Teste B';email="b-$suffix@example.test";password=$password}
$ha=@{Authorization="Bearer $($first.accessToken)"};$hb=@{Authorization="Bearer $($second.accessToken)"}
$account=Post '/accounts' @{name='Conta teste';type='CHECKING';openingBalance=100} $ha
$key=[guid]::NewGuid().ToString();$ha['Idempotency-Key']=$key
$body=@{accountId=$account.id;type='EXPENSE';amount='20.00';date='2026-11-07';description='Teste'}
$t=Post '/transactions' $body $ha
$again=Post '/transactions' $body $ha
if($t.id -ne $again.id){throw 'Idempotência falhou'}
$foreign=Invoke-WebRequest -Uri ($base+'/transactions') -Method Post -Headers ($hb+@{'Idempotency-Key'=[guid]::NewGuid().ToString()}) -ContentType 'application/json' -Body ($body|ConvertTo-Json) -SkipHttpErrorCheck
if([int]$foreign.StatusCode -ne 404){throw "Isolamento falhou: $($foreign.StatusCode)"}
$accounts=Invoke-RestMethod -Uri ($base+'/accounts') -Headers $ha
if($accounts[0].balance -ne 80){throw 'Saldo incorreto'}
$new=Post '/auth/refresh' @{refreshToken=$first.refreshToken}
$replay=Invoke-WebRequest -Uri ($base+'/auth/refresh') -Method Post -ContentType 'application/json' -Body (@{refreshToken=$first.refreshToken}|ConvertTo-Json) -SkipHttpErrorCheck
if([int]$replay.StatusCode -ne 401){throw 'Replay aceito'}
$revoked=Invoke-WebRequest -Uri ($base+'/auth/refresh') -Method Post -ContentType 'application/json' -Body (@{refreshToken=$new.refreshToken}|ConvertTo-Json) -SkipHttpErrorCheck
if([int]$revoked.StatusCode -ne 401){throw 'Família não foi revogada'}
$accessRevoked=Invoke-WebRequest -Uri ($base+'/accounts') -Headers $ha -SkipHttpErrorCheck; if([int]$accessRevoked.StatusCode -ne 401){throw 'JWT da família revogada ainda aceito'}
'PASS: cadastro, isolamento entre usuários, idempotência, saldo, rotação e revogação por replay.'
'Os usuários de teste permanecem apenas no banco isolado de validação.'

