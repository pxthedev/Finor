# Operação atual — Firestore

A configuração ativa mudou para Cloud Firestore. Siga [FIREBASE.md](FIREBASE.md) para IAM, regras, backups e desenvolvimento. As notas PostgreSQL abaixo são legadas e só se aplicam ao perfil SQL.

# Operação

Configure segredos fora do repositório. JWT_SECRET deve ter alta entropia e pelo menos 64 caracteres. Sua troca invalida tokens de acesso existentes; planeje comunicação de reautenticação.

Publique a API atrás de proxy HTTPS. Bloqueie acesso direto à porta interna no ambiente de produção. O container roda com UID sem privilégios. Não use a configuração de testes com autenticação trust fora do banco descartável local.

Backups: use a rotina do PostgreSQL gerenciado ou `pg_dump --format=custom` com credenciais via configuração segura do cliente. Criptografe o destino, defina retenção e execute regularmente restaurações em banco isolado. Um arquivo de backup sem restauração testada não é evidência de recuperação.

As previsões mensais são geradas até os próximos 12 meses e renovadas pelo job diário às 03:15 America/Sao_Paulo. Dia 29/30/31 se ajusta ao último dia do mês. Parar uma série mantém o histórico e remove ocorrências futuras pendentes.

A limitação local de requisições é por IP e por processo. Em múltiplas réplicas, adicione limite no gateway ou armazenamento compartilhado. Não configure confiança irrestrita em cabeçalhos de proxy. Logs não devem incluir corpos de requests, cabeçalhos de autorização ou mensagens do chat.

Monitorar: erros HTTP, latência, conexões do banco, migrações, job de recorrências e entregas SMTP. Credenciais do provedor de IA devem ter limites de gasto e alertas; o servidor limita mensagem, chamadas por usuário e tokens de saída.

Antes das lojas: valide builds EAS em dispositivos, recuperação de senha, exclusão de conta, perda de rede, retomada de sessão, acessibilidade com leitor de tela e valores monetários de limite. Revise as dependências auditadas e faça testes de restauração/exportação.

Use SPRING_PROFILES_ACTIVE=prod no backend de produção: HTTPS é exigido e a documentação interativa fica desativada. O proxy confiável deve remover cabeçalhos Forwarded/X-Forwarded enviados pelo cliente e definir os seus próprios; mantenha o backend inacessível diretamente. O aplicativo compilado para produção também recusa uma API sem HTTPS.


Atualização do SDK seguiu o fluxo oficial: https://docs.expo.dev/workflow/upgrading-expo-sdk-walkthrough/ . As dependências nativas foram alinhadas e validadas com expo install --check; as dependências de ferramentas glob/minimatch são fixadas via overrides e devem ser revalidadas nos próximos upgrades.


