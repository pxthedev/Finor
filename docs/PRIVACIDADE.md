# Privacidade do Finor — minuta para personalização

O operador deve preencher antes da publicação: razão social/nome do controlador, contato de privacidade, endereço, fornecedores, país de hospedagem e períodos de retenção. Esta minuta descreve o funcionamento implementado e requer revisão para o serviço efetivamente oferecido.

O Finor trata nome, e-mail, hash de senha, registros financeiros fornecidos pelo usuário e mensagens enviadas ao assistente. Não solicita credenciais bancárias e não movimenta dinheiro.

Os dados são usados para autenticar, organizar registros, calcular relatórios e disponibilizar o assistente solicitado pelo usuário. A mensagem enviada ao assistente é transmitida ao provedor de IA configurado pelo operador. Evite incluir documentos, senhas ou dados de terceiros nas mensagens. As condições e retenção do provedor precisam ser divulgadas pelo operador.

O aplicativo permite consultar o perfil, exportar os registros e excluir a conta mediante confirmação e senha. A exclusão remove os registros do banco ativo e revoga as sessões. Backups seguem o período de retenção definido pelo operador e devem expirar conforme sua política operacional.

Comunicações de recuperação de acesso são enviadas pelo provedor SMTP configurado. O aplicativo mantém a sessão no armazenamento seguro do dispositivo e transmite dados à API pela conexão configurada; produção deve utilizar HTTPS.

Solicitações adicionais sobre dados pessoais devem ser encaminhadas ao contato de privacidade que será publicado pelo controlador. O suporte e a política definitiva devem estar acessíveis também pelas páginas públicas indicadas nas lojas.

Arquitetura atual do banco: Google Cloud Firestore (Firebase), acessado somente pela API Java. O provedor do assistente é GroqCloud. O controlador deve informar a região do banco, os fornecedores efetivamente contratados, retenção e canal de contato antes da publicação. As credenciais de serviço não são incluídas no aplicativo mobile.


