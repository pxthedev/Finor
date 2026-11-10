# Validação atual — Firebase / Cloud Firestore

A configuração padrão do backend é firestore; a implementação PostgreSQL/Supabase permanece legada, desativada nesse perfil.

## Resultados locais

- Maven package: BUILD SUCCESS.
- 30 testes encontrados: **25 passaram**, 5 ignorados (integrações externas e testes PostgreSQL opcionais).
- Seis testes integrados executados contra o **emulador oficial Firestore 1.22.0**, com a API Spring Boot real: isolamento entre usuários, idempotência, exclusão/reenvio, refresh rotativo e replay, redefinição de senha e revogação, planejamento, orçamento, exportação/exclusão de conta, confirmação da IA, concorrência e bloqueio de acesso direto ao banco.
- A GroqCloud é substituída somente no teste integrado do assistente, para não consumir créditos ou depender de rede; o adaptador real é usado na aplicação.
- Os scripts scripts/smoke.ps1 e scripts/finance-smoke.ps1 passaram contra o JAR empacotado usando Firestore. Incluem transferências, gastos futuros, recorrências e filtros.
- As regras do emulador recusaram leitura direta de cliente sem autorização Admin (HTTP 403).

## Não validado em produção

Não houve conexão/deploy no projeto Firebase do usuário: FIREBASE_PROJECT_ID e credenciais de serviço ainda não foram fornecidos. O emulador não comprova IAM, índices, quotas ou concorrência de produção. Consulte FIREBASE.md antes de implantar.

Use a validação real com uma frase fictícia para conferir o acesso ao modelo GroqCloud. SMTP real, instalação em aparelhos e builds assinados nas lojas continuam pendentes. A chave previamente encontrada em arquivo de exemplo foi transferida para arquivo privado e deve ser substituída antes da produção.

As verificações mobile anteriores passaram com Expo 58, TypeScript e bundles JS Android/iOS. Esta alteração de banco manteve as rotas da API e não adicionou SDK Firebase nem segredos ao aplicativo. Os bundles anteriores eram de validação, com domínio reservado, e não devem ser publicados como configuração de produção.

