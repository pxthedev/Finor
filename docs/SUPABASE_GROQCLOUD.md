# Finor com Supabase e GroqCloud

Arquitetura: aplicativo Expo → API Java → PostgreSQL Supabase. A API Java também chama a GroqCloud. Cadastro, login, JWT, refresh e autorização continuam no Spring Security; Supabase é o banco, não o serviço de autenticação do aplicativo. Não é necessário instalar supabase-js nem colocar anon key/service_role no mobile.

## 1. Banco Supabase

No painel do seu projeto, abra **Connect** e copie os dados de **Direct connection** ou **Session pooler**, na porta 5432. Não use o Transaction pooler da porta 6543 neste backend: Flyway, prepared statements e o estado de sessão exigem a conexão selecionada.

Preencha o `.env` privado na raiz:

```dotenv
SUPABASE_JDBC_URL=jdbc:postgresql://HOST_DO_PAINEL:5432/postgres
SUPABASE_DB_USER=USUARIO_DO_PAINEL
SUPABASE_DB_PASSWORD=
SUPABASE_SSL_ROOT_CERT=C:/caminho/para/certificado-supabase.crt
DATABASE_POOL_SIZE=5
JWT_SECRET=
```

Esses valores de host/usuário são exemplos e devem ser substituídos. Para conexão direta o usuário normalmente é postgres; no Session pooler, copie o usuário completo que aparece no painel. Use a **senha do banco**, não a chave de API do projeto. A URL deve ser JDBC, sem usuário/senha, query string ou parâmetros. O backend força `sslmode=verify-full` e recebe o certificado separadamente.

Baixe o certificado raiz no painel de configurações do banco e defina seu caminho em SUPABASE_SSL_ROOT_CERT. Use exatamente o certificado do projeto e o hostname do painel. O Compose monta o arquivo somente para leitura. JWT_SECRET precisa ter pelo menos 64 caracteres aleatórios; não use a chave JWT do Supabase como substituta automática.

O perfil Spring `supabase` cria o schema **finor** e aplica as migrações nele. As tabelas não são criadas no schema public. Um callback Flyway revoga acesso de PUBLIC, anon e authenticated ao schema e suas tabelas, em cada execução das migrações. **Não adicione finor aos schemas expostos da Data API/GraphQL.** Se não usar a Data API neste projeto, desative-a pelo painel. O backend conecta por JDBC; a autorização de cada registro é feita pelo usuário autenticado na API Java. Não há políticas baseadas no auth.uid() do Supabase, pois Supabase Auth não é utilizado.

Em projeto já existente com dados em public, este perfil não move esses dados automaticamente. Planeje a migração antes de trocar o backend de um sistema em produção.

## 2. GroqCloud

```dotenv
GROQ_API_KEY=
GROQ_MODEL=openai/gpt-oss-20b
GROQ_CHAT_COMPLETIONS_URL=https://api.groq.com/openai/v1/chat/completions
```

O modelo padrão é configurável; confirme que sua conta tem acesso e créditos. O backend usa a Chat Completions API oficial, function calling com strict=true, um único tool call, campos opcionais como null, max_output_tokens e validação dos argumentos no Java. A chave fica somente no Java. O endereço é restrito ao endpoint oficial para impedir envio acidental da chave a outro servidor.

A GroqCloud interpreta a mensagem. O backend valida a estrutura, calcula os números, verifica o proprietário e exige confirmação para registrar transações. Uma resposta incompleta, recusada ou inválida não gera uma operação financeira.

validação dos argumentos no Java evita o armazenamento da resposta como estado recuperável da Chat Completions API; não representa garantia de ausência de retenção operacional/monitoramento pelo provedor. O histórico do aplicativo fica no banco Supabase.

## 3. Executar

```powershell
docker compose -f docker-compose.supabase.yml up --build
```

Esse arquivo é **independente** do docker-compose.yml local e não cria outro PostgreSQL. Usa o .env da raiz. A API fica em localhost:8080 para desenvolvimento. Configure EXPO_PUBLIC_API_URL no mobile com o endereço acessível do seu backend Java, nunca com a URL do projeto Supabase.

Em produção, use SPRING_PROFILES_ACTIVE=prod,supabase, domínio HTTPS, proxy confiável e segredos na plataforma. No Java sem Docker, carregue as variáveis no processo e ative o perfil supabase; o Spring não carrega automaticamente o .env da raiz.

## 4. Validação real

```powershell
pwsh -File scripts/validate-integrations.ps1 -Only Database
pwsh -File scripts/validate-integrations.ps1 -Only AI
```

Database valida conexão e TLS com consultas somente de leitura; não cria tabelas nem aplica migrações. As migrações são aplicadas ao iniciar a API. AI envia uma frase financeira fictícia ao adaptador real; não cria transações e pode consumir créditos.

SMTP continua sendo uma integração separada. Escolha o provedor, preencha SPRING_MAIL_*, MAIL_FROM e SMTP_TEST_TO e execute `-Only SMTP`. A senha do banco Supabase e a chave da GroqCloud não são credenciais SMTP. O teste envia apenas uma mensagem neutra para o destinatário explicitamente configurado; confirme o identificador recebido para validar a entrega.

Nenhuma integração externa está validada apenas por preencher exemplos. Os testes reais só executam com suas credenciais.

## Referências oficiais

- [GroqCloud: function calling e schemas estritos](https://console.groq.com/docs/tool-use/overview)
- [GroqCloud: Chat Completions API](https://console.groq.com/docs/api-reference)
- [GroqCloud: modelos suportados](https://console.groq.com/docs/models)
- [Supabase: conexões e certificados TLS](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase: segurança da Data API](https://supabase.com/docs/guides/api/securing-your-api)


HTTP 429 na validação da GroqCloud pode indicar limite de requisições ou cota do projeto; não implica erro no schema por si só. Confira o painel do seu projeto e não faça tentativas contínuas. Referência: https://console.groq.com/docs/errors .




