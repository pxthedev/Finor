# Finor para Android e iOS

O mesmo projeto Expo/React Native em `finor-mobile` atende Android, iPhone e iPad. As duas plataformas usam a mesma API Java e o mesmo usuário Firebase. Chat, categorias, metas, parcelas e foto de perfil compartilham os registros da conta.

## APK Android para instalação

O [build Android preview](https://expo.dev/accounts/voltarellidev/projects/finor/builds/e37578ac-6869-4c81-8cc5-2ecca66c6a40) foi concluído. [Baixar finor.apk](https://expo.dev/artifacts/eas/KTMGKmkmfVyRz9bYvJkwOuciB1p-w2yvwceENhzVJwg.apk). Cópia local: `artifacts/android/finor.apk` (96 MB).

É um APK release assinado, com JavaScript incluído e API `https://finor.discloud.dev/api/v1`; funciona sem Metro, USB ou computador. `scripts/verify-android-apk.ps1` conferiu assinatura, pacote `com.finor.app`, ausência de modo debug e configuração Google/API. O SHA-1 do certificado foi cadastrado no Firebase e o cliente OAuth Android correspondente foi confirmado. O login em aparelho físico ainda precisa ser testado.

Para gerar novamente, use `scripts/eas-android-build.ps1 -StartBuild` e acompanhe com `scripts/eas-android-build.ps1 -Watch`. A assinatura é mantida no EAS para permitir atualizações. Se houver uma versão debug instalada com outro certificado, o Android exigirá removê-la antes de instalar este APK.

## Gerar o arquivo .ipa do finor

### Teste pessoal com Apple ID gratuito (Windows)

O fluxo padrão de `npm run build:ipa` prepara um **IPA sem assinatura** no EAS, com o projeto `finor` e Bundle ID `com.finor.app`. O perfil `ios-unsigned` usa um build personalizado e não solicita certificados Apple. O arquivo resultante `finor-unsigned.ipa` precisa receber uma assinatura pessoal antes de instalar no iPhone.

1. Entre na sua conta Expo e vincule o projeto com `npx eas-cli login` e `npx eas-cli init`.
2. Configure a API HTTPS e o Firebase real no ambiente EAS `preview`, conforme descrito abaixo. A assinatura gratuita não altera a configuração do servidor.
3. Execute `npm run build:ipa` em `finor-mobile` e baixe o artefato após o build concluir.
4. No Windows, use o [Sideloadly oficial](https://sideloadly.io/) para carregar `finor-unsigned.ipa`, assinar com seu Apple ID gratuito e instalar no seu iPhone conectado. Faça o login diretamente no programa.
5. Use o mesmo identificador `com.finor.app` e a mesma conta ao renovar a instalação. Confira o identificador nas opções do programa antes de assinar, pois alterá-lo afeta integrações como Google.

A conta pessoal permite testar o próprio app, com perfis que expiram após sete dias e precisam ser renovados. Esse fluxo não publica o app nem habilita TestFlight. O Sideloadly documenta os componentes Apple necessários no Windows e a renovação automática. A compilação na nuvem foi concluída com sucesso em 09/11/2026; a instalação no iPhone ainda precisa ser realizada.

Build concluído: [EAS — IPA do finor](https://expo.dev/accounts/voltarellidev/projects/finor/builds/425e8e77-c2db-4583-af4c-ac99a39e2f0b). [Baixar finor-unsigned.ipa](https://expo.dev/artifacts/eas/j_0YU3SIY5zw6pzEhG-bJHm24v_9A-SmWbCHfmiAk-Y.ipa). O arquivo também é salvo em `artifacts/ios/finor-unsigned.ipa` pelo script `scripts/eas-ios-build.ps1`.

Os pacotes Capacitor permanecem instalados, mas são excluídos do autolinking Expo para não inserir os pods Capacitor/CapacitorCordova no app React Native.

O projeto já está vinculado à conta Expo `voltarellidev`, com ID `e40941cd-113c-46e9-ac62-869cf2fcd3f1`. O ambiente `preview` usa a chave Web Firebase do próprio app e desativa as exceções HTTP de desenvolvimento. A API foi publicada em `https://finor.discloud.dev/api/v1`; cadastro, autenticação e leitura do perfil no Firestore foram testados com uma conta temporária e a conta foi removida.

O domínio atribuído pela hospedagem é `finor.discloud.dev`. O endereço `.app` informado inicialmente não resolveu no DNS. O pacote de publicação e as credenciais necessárias estão descritos em [DISCLOUD.md](DISCLOUD.md).

Quando a API HTTPS estiver disponível, este comando na raiz do workspace confere a resposta autenticada esperada, configura as variáveis públicas no projeto Expo e solicita o build:

```powershell
.\scripts\configure-ios-preview.ps1 -ApiUrl 'https://finor.discloud.dev/api/v1' -StartBuild
```

Substitua a URL pelo endereço real. Configure `EXPO_PUBLIC_FIREBASE_API_KEY` no ambiente `preview` do EAS. Para atualizar essa variável pelo script, defina a chave Web no ambiente do PowerShell antes de executá-lo.

Se tiver um Mac, também pode usar sua conta gratuita como **Personal Team** no Xcode, escolher o iPhone e executar `npm run ios -- --device`. Nesse caso, o Xcode instala diretamente o app de desenvolvimento e não é necessário exportar um IPA de distribuição.

Fontes: [conta pessoal e limites da Apple](https://developer.apple.com/help/account/basics/about-your-developer-account), [Sideloadly](https://sideloadly.io/faq.html) e [builds personalizados do EAS](https://docs.expo.dev/custom-builds/schema/).

### Distribuição assinada com Apple Developer Program

Nome de exibição iOS: `finor`. Bundle ID: `com.finor.app`. Esses valores são conferidos por `npm run ipa:check` e `npm run check:platforms`.

Na pasta `finor-mobile`:

```sh
# Conferir a configuração e as pendências, inclusive no Windows:
npm run ipa:check

# Build assinado na nuvem para iPhones registrados na assinatura:
npm run build:ipa:signed

# Build na nuvem para App Store/TestFlight, sem publicação automática:
npm run build:ipa:store
```

Esses comandos EAS exigem o projeto vinculado à sua conta Expo, a assinatura Apple configurada e as variáveis públicas de backend/Firebase descritas abaixo. O resultado é um IPA para dispositivo físico; o perfil de simulador gera um `.app`, não um IPA instalável em iPhone.

Também foi preparado um comando local para um Mac com Xcode completo, CocoaPods e a conta Apple configurada no Xcode. Com as variáveis reais de produção carregadas no `.env` do mobile:

```sh
export APPLE_TEAM_ID=SEUTEAMID1
npm run ipa:mac
# Ou exportar para App Store/TestFlight, sem enviar automaticamente:
npm run ipa:mac -- --method=app-store-connect
```

Substitua `SEUTEAMID1` pelo Team ID real da Apple. O script valida a API HTTPS, gera o projeto nativo, compila em Release, cria o arquivo de assinatura/exportação e exporta `finor-mobile/build/ios/<data-hora>/export/finor.ipa`. Cada execução usa uma pasta nova. O script não envia o IPA para a loja. O build local não foi executado neste Windows; a configuração e os comandos de pré-verificação foram validados.

### Capacitor instalado

O `capacitor.config.ts` já contém `appName: 'finor'` e `appId: 'com.finor.app'`. O projeto atual usa React Native/Expo e seus módulos nativos. O Capacitor exige um diretório web com `index.html`; a configuração atual aponta para `www`, que não é gerado por este app.

Por isso, os comandos de IPA acima compilam o app nativo Expo. Os pacotes Capacitor instalados foram preservados. Usar Capacitor para este produto exigiria preparar uma versão web e adaptar os módulos de autenticação, armazenamento e fotos. Não use `cap add ios` na mesma pasta `ios` do Expo: são projetos nativos diferentes.

Referências: [requisitos do Capacitor](https://capacitorjs.com/docs/getting-started), [build iOS com Expo](https://docs.expo.dev/build-reference/ios-builds/) e [exportação de arquivos com Xcode](https://developer.apple.com/library/archive/technotes/tn2339/_index.html).

## O que está preparado

- Identificador Android e iOS: `com.finor.app`.
- Teclado, formulários, áreas seguras e barra inferior adaptados às duas plataformas.
- Seletor de fotos, recorte e armazenamento da imagem vinculados à conta.
- Login por e-mail/senha nas duas plataformas. Google depende dos clientes OAuth de cada plataforma, conforme [GOOGLE-LOGIN.md](GOOGLE-LOGIN.md).
- Perfis EAS para testes internos, simulador iOS e versões das lojas. Os comandos de build não publicam o app automaticamente.

## O que é possível neste Windows

Android pode ser compilado e instalado localmente, como em [TESTAR-ANDROID.md](TESTAR-ANDROID.md). O pacote JavaScript iOS e as configurações de permissões podem ser validados aqui. A compilação nativa e o simulador iOS exigem macOS/Xcode, ou a compilação na nuvem EAS.

A conta Expo e o vínculo do projeto estão configurados e a API HTTPS está publicada. Para o teste pessoal, a conta Apple gratuita assina o artefato posteriormente. A instalação no iPhone ainda precisa ser realizada.

## Testar iOS com um Mac

Instale Xcode e suas ferramentas, Node.js e as dependências do projeto. Em `finor-mobile`, configure `.env` a partir de `.env.example`:

```dotenv
EXPO_PUBLIC_API_URL=http://IP-DO-COMPUTADOR-COM-A-API:8080/api/v1
EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL=http://IP-DO-COMPUTADOR-COM-A-API:9099
EXPO_PUBLIC_FIREBASE_API_KEY=demo-key
FINOR_ALLOW_LOCAL_NETWORK=1
```

Para acessar o Authentication local pelo Wi-Fi, pare a instância anterior desse emulador e inicie no computador da API:

```powershell
.\scripts\auth-emulator.ps1 -Lan
```

Mantenha Firestore e API em execução. O celular e o computador devem estar na mesma rede. Se o Windows solicitar, permita o acesso dos serviços na rede privada. Firestore continua acessível apenas pelo backend; o app não precisa acessar a porta 8085.

No Mac:

```sh
cd finor-mobile
npm ci
npm run ios
# Ou, para um iPhone conectado e configurado no Xcode:
npm run ios -- --device
```

O iPhone usa o endereço IP do computador da API, pois `127.0.0.1` no aparelho aponta para o próprio aparelho. No simulador, `127.0.0.1` funciona se os serviços também estiverem no Mac. `adb reverse` é exclusivo do Android.

No Android por USB, mantenha o fluxo atual de `TESTAR-ANDROID.md`. Não troque a configuração do Android que já funciona para o endereço de outro computador sem ajustar os serviços.

## Gerar Android e iOS na nuvem futuramente

1. Crie sua conta Expo. Para distribuição no iPhone, configure sua conta Apple Developer e a assinatura. Um build de simulador não exige Apple Developer, mas o simulador precisa de um Mac.
2. Dentro de `finor-mobile`, execute `npx eas-cli login` e `npx eas-cli init` para vincular o projeto. Não cole senhas ou credenciais no código.
3. Disponibilize a API por HTTPS. No painel Expo, configure as variáveis públicas nos ambientes `preview` e `production`:
   - `EXPO_PUBLIC_API_URL`: URL real da API, incluindo `/api/v1`.
   - `EXPO_PUBLIC_FIREBASE_API_KEY`: chave Web do projeto Firebase real.
   - `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` e `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID`: se usar Google.
4. Remova `EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL` e mantenha `FINOR_ALLOW_LOCAL_NETWORK` ausente ou `0` nesses ambientes. Chaves GroqCloud e credenciais Admin Firebase ficam no servidor.

O arquivo `.easignore` envia o código compartilhado e deixa o EAS gerar cada projeto nativo no ambiente adequado. As correções de caminhos CMake do Windows não são aplicadas nos servidores Linux/macOS.

| Comando em `finor-mobile` | Resultado |
| --- | --- |
| `npm run build:android:preview` | APK Android para teste interno |
| `npm run build:ios:preview` | IPA para iPhones registrados no perfil de assinatura |
| `npm run build:preview` | Builds internos Android e iOS |
| `npm run build:ios:simulator` | App para simulador iOS no Mac |
| `npm run build:production` | AAB Android e IPA para distribuição pelas lojas |

Antes de gerar um IPA interno, registre o iPhone com `npx eas-cli device:create`. O EAS orienta a configuração da assinatura. A publicação na Play Store/App Store e o envio ao TestFlight são etapas separadas e não foram executados.

## Validar sem contas das lojas

```sh
cd finor-mobile
npm run typecheck
npm run test:platforms
npm run check:platforms
npm run export:mobile
```

O export gera os pacotes JavaScript Android/iOS em `dist-mobile-check`. Ele não substitui a compilação nativa nem os testes em um iPhone. O ambiente de distribuição é validado para impedir builds apontando para localhost, HTTP ou o emulador de autenticação.

Referências: [configuração EAS](https://docs.expo.dev/build/eas-json/), [distribuição interna iOS](https://docs.expo.dev/build/internal-distribution/) e [simulador iOS](https://docs.expo.dev/build-reference/simulators/).
