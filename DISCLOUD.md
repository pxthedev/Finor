# Finor API na Discloud

API publicada e testada: `https://finor.discloud.dev/api/v1`. A Discloud atribuiu o domínio `.dev`; o endereço `.app` informado inicialmente não resolveu no DNS. O ambiente Expo `preview` já usa a URL ativa.

Em 09/11/2026, o serviço `finor` iniciou com os perfis `firestore,prod`, 1024 MB e Java 25. Cadastro Firebase, autenticação, acesso ao Firestore e leitura de perfil foram verificados com uma conta temporária, removida ao concluir. O endpoint `/users/me` sem autenticação retorna HTTP 401.

## Pacote preparado

Execute na raiz do workspace:

```powershell
.\scripts\package-discloud.ps1
```

O script compila o JAR e cria um ZIP em `artifacts/discloud/`. O arquivo contém `discloud.config`, `app.jar` e `start.sh` diretamente na raiz. O perfil usa `TYPE=site`, `ID=finor`, 1024 MB e `VERSION=latest`. O script de inicialização verifica Java 21+, carrega o `.env`, exige a credencial Firebase e inicia em `0.0.0.0:8080` com os perfis `firestore,prod`.

O plano da Discloud deve permitir sites e ter a RAM configurada disponível. A documentação oficial exige Platinum ou superior para APIs. A versão Java realmente fornecida pela hospedagem precisa ser 21 ou superior; a verificação acontece na inicialização.

## Credencial do Firebase

A aplicação atualmente usa o projeto `finance-app-7ebf6`, configurado no mobile. Obtenha a chave privada da conta de serviço em [Firebase — Configurações do projeto — Contas de serviço](https://console.firebase.google.com/project/finance-app-7ebf6/settings/serviceaccounts/adminsdk), com acesso a Firestore e Firebase Authentication.

Salve o JSON localmente em `.secrets/firebase-admin.json`, que está excluído do controle de versão. Não envie esse JSON no chat. Com esse arquivo presente, execute novamente:

```powershell
.\scripts\package-discloud.ps1
```

O pacote privado passará a incluir `credentials/firebase-admin.json` e um `.env` próprio do servidor, contendo apenas a configuração Firebase e as variáveis GroqCloud necessárias. O script não copia o `.env` inteiro do workspace. Um pacote com credenciais é exclusivo do servidor e não deve ser enviado ao aplicativo mobile ou publicado em repositórios.

Sem o JSON, o ZIP é um modelo sem segredos: inclui `.env.example`, mas não está pronto para iniciar em produção. O campo `ProductionConfigurationReady` da saída do script mostra essa condição.

## Publicar

O CLI precisa estar autenticado na conta que reservou o domínio. Se necessário, autentique localmente:

```powershell
discloud login
```

Informe o token diretamente no terminal, conforme orientado pelo CLI. Depois de gerar o pacote completo, envie o ZIP pelo painel Discloud. Como alternativa, entre na pasta indicada em `Directory` na saída do empacotador e use:

```powershell
discloud app upload --glob discloud.config app.jar start.sh .env credentials/firebase-admin.json LEIA-ME.txt
```

O empacotador usa o perfil Maven `discloud`, que retira do JAR as dependências SQL não utilizadas no Firestore e grava a compilação em `finor-api/target-discloud/`. Isso reduz o tamanho da transferência sem alterar o build SQL padrão.

Se o CLI interromper o envio, a mesma API oficial pode ser usada com o pacote privado já gerado:

```powershell
.\scripts\upload-discloud-package.ps1 -Package artifacts/discloud/finor-discloud-<data>.zip
```

Confirme a publicação pelo status e pelos logs da Discloud antes de configurar a URL no Expo.

## Conectar ao build iOS

Quando o deploy estiver respondendo, execute:

```powershell
.\scripts\configure-ios-preview.ps1 -ApiUrl 'https://finor.discloud.dev/api/v1' -StartBuild
```

Esse comando exige HTTP 401 em `/users/me` sem autenticação antes de gravar a URL no ambiente Expo. Após a compilação, assine `finor-unsigned.ipa` com sua conta pessoal no Sideloadly para instalar no seu iPhone.

Referências: [Java na Discloud](https://docs.discloud.com/en/development-environment/supported-languages/java), [sites e APIs](https://docs.discloud.com/how-to-host/websites-and-apis) e [login/publicação pelo CLI](https://docs.discloud.com/en/how-to-host-using/cli).
