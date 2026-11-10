# Acesso com Google e Firebase Authentication

O aplicativo Expo usa Firebase Authentication (REST oficial) para e-mail/senha e para trocar o ID token Google por ID token Firebase. O Spring Boot verifica o token Firebase com Admin SDK, incluindo revogação, e associa o UID ao proprietário dos dados financeiros. A chave privada Admin nunca vai para o aplicativo.

## Configuração necessária

1. No projeto Firebase, abra Authentication > Sign-in method e ative **E-mail/senha** e **Google**. Defina o e-mail de suporte do provedor Google.
2. Registre Android com package `com.finor.app` e iOS com bundle `com.finor.app`. Cadastre os fingerprints SHA-1/SHA-256 dos certificados de desenvolvimento e de produção Android (incluindo Play App Signing).
3. No Google Cloud do mesmo projeto, confira o cliente OAuth Web e o cliente OAuth iOS. Configure a tela de consentimento e usuários de teste, quando o aplicativo estiver em teste.
4. Em `finor-mobile/.env`, configure `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`, `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID` e `EXPO_PUBLIC_FIREBASE_API_KEY`. Os IDs OAuth terminam em `.apps.googleusercontent.com`. Obtenha a chave Web nas configurações do projeto Firebase e configure as mesmas variáveis no ambiente EAS usado pelo build.
5. No backend configure `FIREBASE_PROJECT_ID` para o mesmo projeto e `GOOGLE_APPLICATION_CREDENTIALS` para credenciais Admin com acesso a Authentication e Firestore. Use perfil `firestore` (padrão). Remova variáveis de emuladores em produção.
6. Gere um novo build Android/iOS com EAS ou Expo nativo. Alterar os IDs exige atualizar o build. O plugin iOS deriva o URL scheme do ID OAuth iOS. Não usa Firebase SDK nativo: a troca com Firebase usa HTTPS REST.

**Google Sign-In não funciona no Expo Go.** O botão informa isso quando o módulo nativo não está disponível. A implementação Google desta versão é para Android/iOS; acesso Google em navegador ainda não está implementado.

## Fluxo e segurança

- O botão “Continuar com Google” aparece tanto no login quanto no cadastro. O acesso cria ou carrega o perfil financeiro e abre o início.
- Cancelar a seleção de conta não cria uma sessão. Dados financeiros continuam acessíveis exclusivamente pelo backend autenticado.
- Sessões ficam no SecureStore, com renovação pelo Firebase. As sessões JWT antigas são descartadas pelo novo namespace local.
- `POST /api/v1/auth/bootstrap` cria/associa o perfil financeiro após verificar o token; `POST /api/v1/auth/logout` revoga as sessões Firebase do usuário. Demais endpoints financeiros permanecem com os mesmos caminhos.
- Recuperação de senha usa o e-mail/link enviado pelo Firebase Authentication; não depende do SMTP do Spring.
- Exclusão pede reautenticação com o mesmo UID e exige `auth_time` recente no servidor. Remove a identidade Firebase e os registros financeiros, com retomada da limpeza em caso de falha.
- Contas financeiras antigas não são vinculadas apenas por coincidência de e-mail. Elas exigem migração administrativa explícita de identidades; essa importação ainda não foi implementada. O acesso apresenta conflito em vez de expor dados.
- `firestore-legacy-auth`, combinado com `firestore`, mantém autenticação antiga apenas para compatibilidade e testes antigos. O aplicativo atualizado exige o modo Firebase Authentication padrão.

## Validação

Em 09/11/2026, o provedor Google foi conferido ativo em `finance-app-7ebf6`. O app iOS `com.finor.app` foi registrado e os clientes OAuth Web/iOS oficiais foram recuperados do Firebase. As variáveis `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` e `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID` estão no ambiente Expo `preview` e no `.env` local ignorado pelo controle de versão.

Para recuperar e aplicar novamente a configuração:

```powershell
.\scripts\inspect-google-login.ps1
.\scripts\configure-google-login.ps1 -StartBuild
```

O primeiro script usa a credencial Admin apenas no computador e grava os IDs públicos em `artifacts/google-login-config.json`. Não copia a chave privada para o app. Após o build, `scripts/verify-google-ipa.ps1` confere os dois IDs no bundle, o Bundle ID e o callback Google no `Info.plist`.

No Sideloadly, preserve o Bundle ID `com.finor.app`. Alterá-lo exige outro cliente OAuth iOS correspondente ao novo identificador.

O [build iOS com Google configurado](https://expo.dev/accounts/voltarellidev/projects/finor/builds/425e8e77-c2db-4583-af4c-ac99a39e2f0b) foi concluído. O IPA foi baixado para `artifacts/ios/finor-unsigned.ipa` e passou na verificação dos IDs no JavaScript, do callback Google no `Info.plist`, de `com.finor.app` e da URL HTTPS da API. É necessário reinstalar esse IPA; o anterior foi gerado sem os IDs OAuth.

O APK Android release também foi gerado com o cliente Web no bundle. O app Android `com.finor.app` foi registrado no Firebase com o SHA-1 `78b9f18d4c0ddbc9394bb2e3544ac22888f20e82` do certificado EAS. A configuração retornada pelo Firebase confirmou o cliente OAuth Android para esse pacote e certificado; os dados públicos estão em `artifacts/google-android-config.json`. Ao mudar a assinatura (por exemplo, para Play App Signing), cadastre também o novo certificado.

TypeScript e testes Java verificam compilação, mapeamento da identidade verificada e exigência de reautenticação. Os testes Firestore anteriores usam explicitamente o perfil de autenticação legado. O login Google real precisa ser validado em dispositivo/build nativo após configurar OAuth, certificados e provedor; não foi realizado com conta Google neste ambiente.

Fontes: https://firebase.google.com/docs/reference/rest/auth e https://react-native-google-signin.github.io/docs/setting-up/expo
