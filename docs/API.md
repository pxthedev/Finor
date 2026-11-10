# API Finor v1

Todas as rotas financeiras exigem `Authorization: Bearer ACCESS_TOKEN`. UUIDs de outros usuários são rejeitados. Valores monetários de entrada podem ser strings decimais, como `"45.90"`.

## Acesso

- POST `/auth/register`: `{ "name":"Ana", "email":"ana@example.com", "password":"senha-de-12-ou-mais-caracteres" }`
- POST `/auth/login`: `{ "email":"ana@example.com", "password":"..." }`
- POST `/auth/refresh` e `/auth/logout`: `{ "refreshToken":"..." }`
- POST `/auth/forgot-password`: `{ "email":"ana@example.com" }`
- POST `/auth/reset-password`: `{ "token":"codigo-recebido", "password":"nova-senha..." }`

Login, cadastro e refresh retornam `accessToken`, `refreshToken`, `expiresIn`.

## Finanças

- GET/POST `/accounts`: criação `{ "name":"Principal", "type":"CHECKING", "openingBalance":"1000.00" }` (tipos CHECKING, SAVINGS, CASH).
- GET/POST `/categories`: criação `{ "name":"Alimentação" }`.
- GET `/transactions?page=0`: até 100 registros por página.
- GET `/transactions/{id}`: detalhes.
- POST `/transactions`: exige `Idempotency-Key: UUID`. Corpo `{ "accountId":"UUID", "type":"EXPENSE", "amount":"45.90", "date":"2026-11-07", "description":"Mercado", "categoryId":"UUID" }`.
- PATCH `/transactions/{id}`: mesmo corpo completo da criação; DELETE remove e reverte o efeito no saldo derivado.
- Transferência: type TRANSFER, destinationAccountId obrigatório, categoria ausente.
- GET `/dashboard/summary?month=2026-11`, `/dashboard/categories?month=2026-11`, `/reports/evolution?month=2026-11`.
- GET/POST `/budgets`: consulta usa month; gravação `{ "month":"2026-11", "allocations":[{"categoryId":"UUID","amount":"700.00"}] }`. A gravação substitui o conjunto do mês atomicamente.

## Planejamento

- GET/POST `/planning/schedule`: `{ "monthlyAmount":"3200.00", "nextIncome":"2026-12-05", "remainingEssentials":"200.00", "safetyMargin":"100.00" }`.
- GET `/planning/available`; POST `/planning/simulate`: `{ "amount":"280.00" }`.
- POST `/planning/suggestions`: distribuição calculada sem alterar dados.
- GET/POST `/recurring`: previsão individual `{ "name":"Internet", "amount":"100.00", "dueDate":"2026-11-15", "paid":false }`.
- PATCH `/recurring/{id}`: mesmo corpo completo, para ajustar uma ocorrência.
- POST `/recurring/rules`: mesmo corpo; cria recorrência mensal. DELETE `/recurring/rules/{id}` interrompe a série e remove previsões futuras não pagas.
- GET/POST `/goals`, PATCH `/goals/{id}`: `{ "name":"Viagem", "target":"5000.00", "reserved":"250.00", "deadline":null }`.
- POST `/onboarding`: account, schedule, bills e goal opcional, com os formatos anteriores; só pode ser concluído uma vez.

## Assistente e usuário

- POST `/assistant/messages`: `{ "sessionId":null, "message":"Gastei 45 reais no mercado" }`; retorna sessionId, reply e action quando houver proposta.
- GET `/assistant/sessions`, GET `/assistant/sessions/{id}/messages`, GET `/assistant/sessions/{id}/actions`.
- POST `/assistant/actions/{id}/confirm` ou `/reject`. Confirmar novamente a mesma ação não duplica a transação.
- GET/PATCH `/users/me`; PATCH aceita `{ "name":"Ana" }`.
- GET `/users/me/export`: dados pessoais e financeiros, sem hashes de senha/tokens.
- DELETE `/users/me`: `{ "password":"...", "confirm":true }`.

Erros: 400 validação, 401 sessão inválida, 404 recurso inexistente ou sem propriedade, 409 conflito, 429 limite de solicitações, 503 integração externa não configurada. A documentação OpenAPI contém os esquemas dos corpos.

O histórico aceita filtros opcionais month=AAAA-MM e categoryId=UUID junto com page. Despesas com data futura antes do recebimento entram nas pendências do disponível.

