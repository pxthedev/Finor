$ErrorActionPreference='Stop'
$base='http://127.0.0.1:18089/api/v1'
function Post($path,$body,$headers=@{}) { Invoke-RestMethod -Uri ($base+$path) -Method Post -Headers $headers -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 10) }
function Get($path,$headers){Invoke-RestMethod -Uri ($base+$path) -Headers $headers}
$suffix=[guid]::NewGuid().ToString();$password=[guid]::NewGuid().ToString()+'A!'
$session=Post '/auth/register' @{name='Planejamento';email="plan-$suffix@example.test";password=$password};$headers=@{Authorization="Bearer $($session.accessToken)"}
$today=Get-Date; $date=$today.ToString('yyyy-MM-dd');$next=$today.AddDays(10).ToString('yyyy-MM-dd');$month=$today.ToString('yyyy-MM')
$setup=@{account=@{name='Principal';type='CHECKING';openingBalance='1000.00'};schedule=@{monthlyAmount='3200.00';nextIncome=$next;remainingEssentials='100.00';safetyMargin='50.00'};bills=@(@{name='Internet';amount='100.00';dueDate=$date;paid=$false});goal=@{name='Reserva';target='500.00';reserved='200.00'}}
Post '/onboarding' $setup $headers | Out-Null
$profile=Get '/users/me' $headers;if(!$profile.onboarded){throw 'Onboarding não persistiu'}
$available=Get '/planning/available' $headers;if($available.available -ne 550){throw "Disponível incorreto: $($available.available)"}
$simulation=Post '/planning/simulate' @{amount='280.00'} $headers;if($simulation.remaining -ne 270){throw 'Simulação incorreta'}
$accounts=Get '/accounts' $headers;$a=$accounts[0].id;$b=(Post '/accounts' @{name='Segunda';type='SAVINGS';openingBalance='0.00'} $headers).id
$future=Post '/transactions' @{accountId=$a;type='EXPENSE';amount='50.00';date=$today.AddDays(1).ToString('yyyy-MM-dd');description='Despesa prevista'} ($headers+@{'Idempotency-Key'=[guid]::NewGuid().ToString()})
$protected=Get '/planning/available' $headers;if($protected.available -ne 500){throw 'Despesa futura não foi protegida'}
Invoke-RestMethod -Uri ($base+'/transactions/'+$future.id) -Method Delete -Headers $headers
$transactionHeaders=$headers+@{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$transfer=Post '/transactions' @{accountId=$a;destinationAccountId=$b;type='TRANSFER';amount='100.00';date=$date} $transactionHeaders
$summary=Get '/dashboard/summary' $headers;if($summary.expenses -ne 0 -or $summary.income -ne 0){throw 'Transferência afetou totais'}
$categories=Get '/categories' $headers;$category=$categories[0].id
Post '/budgets' @{month=$month;allocations=@(@{categoryId=$category;amount='300.00'})} $headers | Out-Null
$expenseHeaders=$headers+@{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$expense=Post '/transactions' @{accountId=$a;categoryId=$category;type='EXPENSE';amount='45.90';date=$date;description='Mercado'} $expenseHeaders
$filtered=Get ('/transactions?categoryId='+$category+'&month='+$month) $headers
if(@($filtered).Count -ne 1 -or $filtered[0].id -ne $expense.id){throw 'Filtro de categoria incorreto'}
$budgets=Get ('/budgets?month='+$month) $headers;if($budgets[0].spent -ne 45.90){throw 'Orçamento não atualizou'}
Invoke-RestMethod -Uri ($base+'/transactions/'+$expense.id) -Method Delete -Headers $headers
$budgets=Get ('/budgets?month='+$month) $headers;if($budgets[0].spent -ne 0){throw 'Exclusão não reverteu orçamento'}
$replayed=Post '/transactions' @{accountId=$a;categoryId=$category;type='EXPENSE';amount='45.90';date=$date;description='Mercado'} $expenseHeaders
$all=Get '/transactions' $headers
if(@($all | Where-Object id -eq $replayed.id).Count -ne 0){throw 'Reenvio recriou transação excluída'}
$jan=[datetime]::new($today.Year+1,1,31).ToString('yyyy-MM-dd')
$rule=Post '/recurring/rules' @{name='Mensal';amount='10.00';dueDate=$jan;paid=$false} $headers
$occurrences=Get '/recurring' $headers
if(!($occurrences | Where-Object { $_.rule_id -eq $rule.id -and $_.due_date -match '-02-(28|29)$' })){throw 'Recorrência não ajustou o fim de fevereiro'}
Invoke-RestMethod -Uri ($base+'/recurring/rules/'+$rule.id) -Method Delete -Headers $headers
$remainingRules=Get '/recurring' $headers
if($remainingRules | Where-Object rule_id -eq $rule.id){throw 'Parar série não removeu previsões futuras'}
$export=Get '/users/me/export' $headers;if($export.profile.password_hash){throw 'Exportação vazou hash'}
Invoke-RestMethod -Uri ($base+'/users/me') -Method Delete -Headers $headers -ContentType 'application/json' -Body (@{password=$password;confirm=$true}|ConvertTo-Json)
$result=Invoke-WebRequest -Uri ($base+'/accounts') -Headers $headers -SkipHttpErrorCheck;if([int]$result.StatusCode -ne 401){throw 'Conta excluída ainda autenticada'}
'PASS: onboarding atômico, disponível, simulação, transferências, orçamento, reversão, exportação e exclusão de conta.'


