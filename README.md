# Finor — Firebase / Cloud Firestore

Aplicativo de finanças pessoais em português brasileiro. Frontend Expo/React Native, API Java 21/Spring Boot, **Cloud Firestore Native** e GroqCloud. O mobile se comunica com a API Java; as credenciais do banco e da GroqCloud ficam no servidor.

O app compartilha o código para Android e iOS (iPhone/iPad). Configuração, testes locais e geração dos builds: [ANDROID-IOS.md](ANDROID-IOS.md).

A autenticação continua no Spring Security (JWT, refresh rotativo e BCrypt). Não é necessário adicionar Firebase Auth ou o SDK cliente do Firestore no mobile. Os endpoints `/api/v1` mantêm os fluxos de contas, categorias, receitas, despesas, transferências, planejamento, metas, orçamentos, relatórios, chat e exportação/exclusão.

## Produção

Leia [FIREBASE.md](docs/FIREBASE.md). Crie o banco Firestore Native no projeto e configure no `.env` privado:

```dotenv
SPRING_PROFILES_ACTIVE=firestore,prod
FIREBASE_PROJECT_ID=seu-project-id
FIREBASE_DATABASE_ID=(default)
GOOGLE_APPLICATION_CREDENTIALS=C:/caminho/privado/firebase-service-account.json
JWT_SECRET=
GROQ_API_KEY=
GROQ_MODEL=openai/gpt-oss-20b
```

O segredo JWT local já gerado deve ser mantido em sigilo. Não substitua por chave pública do Firebase. No Google Cloud, prefira identidade de serviço do runtime/ADC sem arquivo de chave. Fora dele, use uma conta de serviço com permissões do Firestore. Nunca coloque esse JSON, a chave GroqCloud ou a senha SMTP no aplicativo.

```powershell
docker compose -f docker-compose.firebase.yml up --build
```

O Compose de produção publica a porta no loopback e exige HTTPS por proxy confiável. Não configure FIRESTORE_EMULATOR_HOST em produção.

## Desenvolvimento com emulador

Instale Firebase CLI e Java 21 e use dois terminais na raiz:

```powershell
firebase emulators:start --only firestore --project demo-finor
# Outro terminal:
pwsh -File scripts/firestore-local.ps1
```

O emulador escuta em 127.0.0.1:8085. Configure o mobile para acessar a API Java (porta 8080), não o emulador do banco. Em aparelho físico use o endereço de rede do computador. Execute `npm ci` e `npx expo start` em `finor-mobile`.

```powershell
# Com emulador ativo, executar os testes do backend:
pwsh -File scripts/firestore-local.ps1 -Tests
```

Esse script usa apenas o projeto fictício demo-finor e gera uma chave JWT de desenvolvimento. Carrega somente as variáveis GROQ_* do .env para o assistente; as credenciais de produção do banco ficam fora da execução local. Os dados do emulador não são dados reais e desaparecem ao reiniciá-lo, a menos que você configure export/import no Emulator Suite.

## Persistência e segurança

- Documentos separados por usuário em `finor_users/{uuid}/...`; IDs externos nunca dão acesso ao caminho de outro usuário.
- Alterações financeiras usam transações nativas do Firestore. O documento raiz coordena as alterações para garantir consistência entre as coleções.
- Valores monetários são decimais exatos em strings com duas casas no banco, calculados em BigDecimal no Java. Não são armazenados como double.
- As chaves de idempotência permanecem após exclusão da transação para impedir recriação por reenvio antigo.
- E-mails únicos têm índice de reivindicação atômico; refresh tokens e códigos de recuperação ficam apenas como hashes.
- Reutilizar refresh revoga a família; redefinir senha invalida as sessões anteriores por versão de credencial.
- Regras Firestore negam acesso direto de clientes; o servidor usa IAM e verifica a propriedade de todos os recursos.
- Exclusão bloqueia o acesso imediatamente, remove documentos em lotes e possui retomada automática para falhas parciais.

O adaptador do MVP lê as coleções necessárias para cálculos e mantém consistência por usuário. Há custo de leituras proporcional ao histórico; faça testes de volume e, antes de escalar, adicione agregados incrementais e consultas por documento/intervalo. Operações muito grandes são rejeitadas antes do commit, sem gravação parcial.

## IA e SMTP

Configure GROQ_API_KEY no backend. A GroqCloud interpreta mensagens com saída estruturada; o Java faz os cálculos e só registra propostas após confirmação. Use scripts/validate-integrations.ps1 -Only AI para validar a conexão com uma frase fictícia, sem registrar transações. O provedor SMTP ainda precisa ser escolhido/configurado. Veja [INTEGRACOES.md](docs/INTEGRACOES.md).

## Compatibilidade e publicação

O perfil `firestore` é o padrão e desativa JPA, JDBC automático e Flyway. Arquivos PostgreSQL/Supabase permanecem como implementação legada, ativada explicitamente com `postgres` ou `supabase`; não são necessários para a configuração Firebase. Não há migração automática de dados existentes entre os bancos.

Antes das lojas, ainda é necessário homologar em aparelhos Android/iOS, validar IAM no projeto Firebase real, testar GroqCloud/SMTP e completar os dados do controlador na política de privacidade. Os testes do emulador não comprovam permissões, limites, índices ou comportamento de concorrência de produção. Não houve deploy no seu projeto Firebase.


Para recompilar e reiniciar a API local em segundo plano, use scripts/restart-local-api.ps1 -Build (com os emuladores ativos).

