# Integrações externas — Firebase e GroqCloud

A configuração ativa é **Cloud Firestore Native + GroqCloud Chat Completions API**, com autenticação e regras de negócio no Java. Veja [FIREBASE.md](FIREBASE.md).

O modelo padrão é `openai/gpt-oss-20b`, hospedado pela GroqCloud. O prefixo faz parte do identificador do catálogo; as requisições e a autenticação usam exclusivamente `api.groq.com` e `GROQ_API_KEY`.

No .env privado preencha FIREBASE_PROJECT_ID, FIREBASE_DATABASE_ID e GOOGLE_APPLICATION_CREDENTIALS (caminho de arquivo de conta de serviço, se não estiver usando ADC do runtime). Para a IA use GROQ_API_KEY e GROQ_MODEL. Nenhuma dessas credenciais deve ser colocada no mobile. A configuração Supabase anterior é legada e não é utilizada pelo perfil firestore.

```powershell
pwsh -File scripts/validate-integrations.ps1 -Only Database
pwsh -File scripts/validate-integrations.ps1 -Only AI
pwsh -File scripts/validate-integrations.ps1 -Only SMTP
```

Database faz uma leitura sem escrita no Firestore real, recusando projeto demo/emulador. AI usa uma frase fictícia com o modelo configurado em GROQ_MODEL, sem registrar transações. A chave GROQ_API_KEY é lida somente no backend.

Para desenvolvimento, `scripts/firestore-local.ps1` carrega as variáveis GROQ_* do arquivo privado `.env`, mantendo banco e autenticação nos emuladores. `scripts/restart-local-api.ps1 -Build` recompila e reinicia a API em segundo plano.

Referências oficiais: [chamadas de ferramentas](https://console.groq.com/docs/tool-use/overview), [API Chat Completions](https://console.groq.com/docs/api-reference).

SMTP continua separado do banco. Configure SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD, MAIL_FROM e SMTP_TEST_TO. O remetente deve estar autorizado. STARTTLS: habilite SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE e desabilite SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE. TLS implícito: inverta conforme a porta do provedor.

O teste SMTP autentica e envia uma mensagem neutra com identificador FINOR-UUID para o destinatário explicitamente configurado. Confirme a chegada desse identificador: aceitação SMTP não comprova entrega na caixa de entrada. Nenhum serviço externo é considerado validado apenas por preencher arquivos de exemplo.


Validação de 08/11/2026: chamadas reais à GroqCloud para uma despesa e uma receita fictícias passaram com o modelo openai/gpt-oss-20b. Nenhuma transação financeira foi gravada pelo teste.
