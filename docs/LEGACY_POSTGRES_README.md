# Finor

**Configuração atual: Supabase PostgreSQL + GroqCloud.** Para essa arquitetura, siga [SUPABASE_GROQCLOUD.md](docs/SUPABASE_GROQCLOUD.md) e use `docker-compose.supabase.yml`. O Compose padrão abaixo continua disponível para desenvolvimento com PostgreSQL local.

Aplicativo de organização financeira em português brasileiro, com React Native/Expo 58 e API Java 21/Spring Boot/PostgreSQL. As telas consultam dados reais da API. Não há saldos de demonstração, conexão bancária ou movimentação de dinheiro.

## Executar em desenvolvimento

1. Copie `.env.example` para `.env` na raiz.
2. Defina `DATABASE_PASSWORD` e `JWT_SECRET` (mínimo 64 caracteres aleatórios). Não reutilize credenciais de outros serviços.
3. Com Docker Desktop ativo, execute `docker compose up --build`.
4. Copie `finor-mobile/.env.example` para `finor-mobile/.env`. Defina o endereço da API acessível pelo dispositivo.
5. Execute `npm ci` e `npx expo start` em `finor-mobile`.

Em aparelho físico, `localhost` aponta para o próprio aparelho. Use um endereço de rede acessível. O Compose publica a API somente no loopback por padrão: para desenvolvimento na rede, ajuste explicitamente a publicação e o firewall. Produção exige um domínio HTTPS.

Sem Docker: configure `DATABASE_URL=jdbc:postgresql://host:5432/finor`, `DATABASE_USER`, `DATABASE_PASSWORD` e `JWT_SECRET`; em `finor-api`, execute `mvn spring-boot:run`. As migrações Flyway são aplicadas no início. `mvn` deve ser executado dentro de `finor-api`; o cache local está configurado em `../.m2`.

## Fluxos implementados

- Cadastro, login, sessão em Secure Store, JWT de 10 minutos, refresh de 30 dias com rotação, logout e revogação por replay. Tokens de acesso verificam a família de sessão no servidor.
- Recuperação de senha por código de uso único enviado por SMTP. Redefinição revoga as sessões.
- Onboarding em cinco etapas, com gravação atômica de conta, renda, previsões e meta.
- Contas, categorias, receitas, despesas, transferências, detalhes, edição e exclusão. Idempotência por usuário e chave UUID; reutilização com dados diferentes retorna 409.
- Saldo derivado do livro de transações. Transferências não entram nos totais de receitas/despesas; lançamentos futuros não aumentam o saldo atual.
- Planejamento, contas previstas, recorrências mensais, reservas de metas, disponível até o próximo recebimento, limite diário e simulação de compra.
- Orçamento mensal por categoria, relatórios mensais, categorias e evolução de seis meses.
- Chat persistido, interpretação por ferramenta estruturada, proposta de transação pendente, confirmação idempotente e cancelamento. Consultas usam cálculos do servidor.
- Perfil, exportação JSON e exclusão de conta com senha e confirmação.

## IA e e-mail

Configure `GROQ_API_KEY` e `GROQ_MODEL` no backend. O adaptador usa a Chat Completions API oficial da GroqCloud com chamada de função e validação dos argumentos no Java. Sem chave, retorna 503. A chave não é enviada ao aplicativo; a IA interpreta mensagens e o Java calcula e confirma operações. 

Para e-mail, configure `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM` e STARTTLS conforme o provedor. Sem SMTP configurado a recuperação retorna 503, sem fingir envio. Credenciais externas não estão incluídas.

## Regras do disponível

`saldo registrado − contas pendentes antes do próximo recebimento − essenciais ainda previstos − reservas das metas − margem`.

Contas pagas não são descontadas novamente. A reserva deve representar dinheiro já incluído no saldo registrado; não cadastre também a mesma reserva como despesa. Marcar uma previsão como paga não cria uma despesa: registre o pagamento na conta para atualizar o saldo. Despesas lançadas com data futura antes do recebimento também são protegidas como pendências; receitas futuras não aumentam o saldo atual. O limite diário divide o disponível não negativo pelos dias até o próximo recebimento. O disponível total preserva déficits. Não há promessa de segurança de compra com dados incompletos.

## API e validação

Prefixo `/api/v1`. Documentação gerada em `/v3/api-docs` e Swagger UI em `/swagger-ui.html`. Use o botão Authorize com o access token. Os principais endpoints e exemplos estão em `docs/API.md`.

- Backend: `mvn test` e `mvn package` dentro de `finor-api`.
- Mobile: `npm run typecheck` dentro de `finor-mobile`.
- Integração HTTP/PostgreSQL: com API na porta 18089 e banco descartável, execute `pwsh -File scripts/smoke.ps1` e `pwsh -File scripts/finance-smoke.ps1`. O primeiro script deixa usuários de teste; não execute em produção.
- Bundles JS: `npx expo export --platform android --platform ios --max-workers 1 --no-bytecode`. Isso não substitui testes nativos.

## Limites e publicação

O código ainda precisa de homologação em dispositivos Android/iOS, teste do provedor de IA com credencial real e envio SMTP real. A renda usa próximo recebimento explícito; não avança automaticamente por frequência. Consultas do disponível usam o horizonte até o próximo recebimento. Não há validação visual em aparelho neste ambiente.

A versão inicial foi atualizada para Expo 58, com dependências alinhadas pela ferramenta oficial. A auditoria npm atual não aponta vulnerabilidades conhecidas. Os overrides de glob 14.0.2 e minimatch 11.0.1 corrigem dependências das ferramentas; mantenha os testes de exportação ao atualizá-los. Isso não substitui homologação em aparelhos nem revisão para publicação.

EAS está configurado para development/preview/production. Para publicar, configure sua conta Expo, project ID, assinatura, identificadores definitivos, páginas de suporte e privacidade, classificação de conteúdo e declarações de dados das lojas. Use PostgreSQL gerenciado com backups verificados, HTTPS no proxy e segredos do ambiente. Leia `docs/OPERACAO.md` e personalize `docs/PRIVACIDADE.md` com os dados do controlador antes de disponibilizar o serviço.



Para incluir o teste de integração Java do assistente, defina FINOR_TEST_DATABASE_URL com a URL JDBC de um PostgreSQL descartável (usuário finor, sem senha no banco local de teste) antes de executar mvn test. Sem essa variável, o teste integrado é ignorado e os seis testes de cálculo continuam disponíveis.


Para configurar e executar testes reais de IA e SMTP, consulte docs/INTEGRACOES.md e execute scripts/validate-integrations.ps1 depois de preencher o .env. Os testes são opt-in e não validam serviços reais enquanto faltarem credenciais.




