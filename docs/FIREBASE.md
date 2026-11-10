# Firebase / Firestore no Finor

## Projeto e credenciais

Use Cloud Firestore Native. Crie o banco (default), escolha a região adequada ao backend e defina FIREBASE_PROJECT_ID. A API usa autenticação de serviço por Application Default Credentials (ADC), nunca uma Firebase Web API Key como senha do banco.

No Google Cloud, associe ao runtime uma identidade com permissões do Firestore (por exemplo, papel Datastore User, ou um papel personalizado com as permissões necessárias). Fora dele, crie uma conta de serviço, guarde o JSON em `.secrets/firebase-service-account.json` fora do código e defina GOOGLE_APPLICATION_CREDENTIALS. O Compose monta esse arquivo somente para leitura. Não publique o arquivo em Git nem no aplicativo.

Ative SPRING_PROFILES_ACTIVE=firestore,prod na produção. O perfil prod exige HTTPS e desativa Swagger público. O perfil firestore exclui autoconfigurações SQL/Flyway; as migrações PostgreSQL anteriores não executam nesse modo.

## Modelo dos documentos

- `finor_users/{userId}`: perfil, hash de senha, versão da credencial e estado ACTIVE/DELETING.
- Subcoleções: accounts, categories, transactions, requests, goals, schedule, bills, rules, budgets, chats, messages, actions, sessions e chat_quota.
- `finor_email_claims/{sha256(emailNormalizado)}`: unicidade do e-mail e localização do usuário.
- `finor_refresh_tokens/{sha256(token)}`: proprietário, família e estado de rotação.
- `finor_password_resets/{sha256(codigo)}`: proprietário, expiração e versão de credencial.

A conta e seus recursos são sempre resolvidos pelo subject do JWT validado. O cliente não fornece o caminho de usuário. Os registros monetários usam strings decimais exatas; UUIDs e datas ISO permanecem compatíveis com a API do mobile.

As transações nativas leem antes de gravar e não chamam IA, SMTP ou outros serviços no callback que pode ser repetido. Um documento raiz por usuário serializa as mutações. Orçamentos são documentos mensais com alocações; a substituição é atômica. Ocorrências recorrentes são projeções determinísticas de regras persistidas; exceções e pagamentos ficam em bills, sem gerar lançamentos duplicados.

O histórico financeiro é a fonte do saldo. Transferências não são receita/despesa. Contas pagas são excluídas das pendências e reservas são descontadas uma única vez. Todos os cálculos são feitos em BigDecimal.

## Regras e índices

`firestore.rules` nega todas as leituras/escritas diretas por clientes. O servidor usa IAM e não depende dessas regras para autorização. Publique as regras antes de disponibilizar o projeto:

```powershell
firebase deploy --only firestore:rules,firestore:indexes --project SEU_PROJECT_ID
```

Esse comando altera o projeto indicado; confira o ID e não execute apontando para outro aplicativo que já use regras próprias. As regras fornecidas são para um projeto no qual os dados Finor são acessados exclusivamente pelo backend.

`firestore.indexes.json` desativa indexação de payloads grandes e hashes de senha desnecessários. As consultas atuais usam caminhos por usuário e consultas de igualdade simples; não exigem índices compostos. O emulador não garante que a configuração de índices ou quotas de produção esteja correta: valide também em um ambiente Firebase de homologação.

## Emulador e teste real

`firebase.json` configura porta 8085, host de loopback e regras restritivas. A API aceita o emulador apenas em projeto demo- e fora do perfil prod. Para testes locais siga o README.

Para conexão real, preencha projeto, banco e credenciais e execute:

```powershell
pwsh -File scripts/validate-integrations.ps1 -Only Database
```

A validação real faz somente uma leitura de um documento inexistente em finor_healthchecks e não consulta dados financeiros. Não roda com projeto demo ou emulador. A leitura pode consumir a cota normal do Firestore. O teste não publica regras, não configura IAM nem cria/migra dados.

## Operação

Configure backups e retenção no Google Cloud e teste restaurações em outro banco/projeto. A exclusão da conta remove os dados ativos; os backups devem expirar conforme a política do controlador. A rotina de exclusão é retomada periodicamente após erros; o usuário permanece bloqueado durante a limpeza.

Para volumes grandes, otimize os snapshots por coleção usados pelo adaptador do MVP com agregados e buscas mais seletivas antes de aumentar a escala. O armazenamento é por documento, não um único documento ilimitado por usuário. O limite de alterações por commit é verificado no servidor para evitar gravações parciais.

Referências oficiais: [SDK servidor](https://firebase.google.com/docs/admin/setup), [transações](https://firebase.google.com/docs/firestore/manage-data/transactions), [emulador e suas limitações](https://firebase.google.com/docs/emulator-suite/connect_firestore).

Neste ambiente, o processo Node do Firebase CLI não conseguiu iniciar Java. O mesmo JAR oficial foi baixado e iniciado diretamente com Java 21. Como alternativa ao CLI, use scripts/firestore-emulator.ps1 -JarPath CAMINHO_DO_JAR_1.22.0; o script verifica o SHA-256 do artefato antes de executá-lo. O download oficial está em https://storage.googleapis.com/firebase-preview-drop/emulator/cloud-firestore-emulator-v1.22.0.jar .

