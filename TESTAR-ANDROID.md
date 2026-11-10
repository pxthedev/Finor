# Testar Finor no Android

Pré-requisitos: Node.js, Java 21, Maven e Android Studio/SDK. Para Google real, configure Firebase/OAuth conforme GOOGLE-LOGIN.md. Os comandos abaixo são para PowerShell.

## Desenvolvimento local completo sem credenciais de nuvem

Abra quatro terminais na raiz `C:\dev\finance-app`.

Terminal 1 — Firestore local:
```powershell
cd C:\dev\finance-app
.\scripts\firestore-emulator.ps1
```

Terminal 2 — Firebase Authentication local:
```powershell
cd C:\dev\finance-app
.\scripts\auth-emulator.ps1
```
O script usa Firebase CLI já instalado em `.tools`; se ausente usa `npx firebase-tools`.

Terminal 3 — API Java:
```powershell
cd C:\dev\finance-app
.\scripts\firestore-local.ps1
```
Os emuladores usam o projeto `demo-finor`. Dados locais são temporários; não use informações financeiras reais. O login por e-mail/senha funciona no emulador; o seletor Google real exige projeto e OAuth reais. IA exige chave GroqCloud no backend; sem configuração, a indisponibilidade deve ser tratada na tela.

Terminal 4 — celular Android por USB:
1. Ative Opções do desenvolvedor e Depuração USB no celular. Conecte o cabo e aceite a autorização RSA.
```powershell
cd C:\dev\finance-app\finor-mobile
adb devices
adb reverse tcp:8080 tcp:8080
adb reverse tcp:8081 tcp:8081
adb reverse tcp:9099 tcp:9099
$env:EXPO_PUBLIC_API_URL='http://127.0.0.1:8080/api/v1'
$env:EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL='http://127.0.0.1:9099'
$env:EXPO_PUBLIC_FIREBASE_API_KEY='demo-key'
npm install
npx expo run:android --device
```
Isso compila e instala um APK de desenvolvimento e inicia o Metro. Em execuções seguintes, mantendo o cabo e os redirecionamentos:

Se houver lentidão ou dois servidores Metro na porta 8081, execute na raiz do projeto `./scripts/restart-local-mobile.ps1`. Ele verifica os processos deste projeto e reinicia apenas o Metro local, com dois processos de compilação. Para validar os endpoints com uma conta temporária do emulador, use `./scripts/check-local-performance.ps1`.
```powershell
npx expo start --localhost
```
Google nativo não funciona dentro do Expo Go. Use o aplicativo compilado. Não use `localhost` do computador no celular por Wi-Fi: ele aponta para o próprio celular. O fluxo USB acima resolve isso com `adb reverse`.

## Usar Firebase real com backend local

Em outro terminal configure as credenciais Admin, sem variáveis de emuladores:
```powershell
cd C:\dev\finance-app
Remove-Item Env:FIRESTORE_EMULATOR_HOST -ErrorAction SilentlyContinue
Remove-Item Env:FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE='firestore'
$env:FIREBASE_PROJECT_ID='SEU_PROJETO'
$env:GOOGLE_APPLICATION_CREDENTIALS='C:\caminho\service-account.json'
# Opcional para testar chat real: $env:GROQ_API_KEY='sua-chave'
mvn '-Dmaven.repo.local=C:\dev\finance-app\.m2' -f finor-api/pom.xml spring-boot:run
```
No terminal mobile remova `EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL` e `EXPO_PUBLIC_FIREBASE_API_KEY` de teste (usa a configuração Web do projeto), configure os IDs Google em `.env` e execute `npx expo run:android --device`. Registre o SHA-1/SHA-256 do certificado de assinatura no Firebase para Google funcionar.

## Gerar APK para transferência

APK de desenvolvimento (precisa do Metro e das configurações anteriores):
```powershell
cd C:\dev\finance-app
.\scripts\build-apk.ps1 -Variant debug
```
Saída: `artifacts\finor-debug.apk`.

APK independente (JavaScript embutido; precisa backend acessível por HTTPS e Firebase/OAuth reais):
```powershell
cd C:\dev\finance-app
$env:EXPO_PUBLIC_API_URL='https://SEU_BACKEND/api/v1'
.\scripts\build-apk.ps1 -Variant release
```
Saída: `artifacts\finor-release.apk`. O projeto gerado pelo Expo usa certificado de debug nesse build local: serve para instalação de teste, não publicação. Não distribua APK com API apontando para 127.0.0.1. O backend não é incorporado ao APK.

Alternativa EAS (build na nuvem, requer conta Expo, projeto vinculado e variáveis públicas configuradas no ambiente preview):
```powershell
cd C:\dev\finance-app\finor-mobile
npx eas-cli login
npx eas-cli build:configure
npx eas-cli build --platform android --profile preview
```

## Passar para o celular

- USB: copie o APK da pasta `artifacts` para Downloads do celular. Abra em Arquivos e autorize “Instalar apps desconhecidos” para esse gerenciador. Instale.
- Com depuração USB: `adb install -r C:\dev\finance-app\artifacts\finor-debug.apk`.
- Build EAS: abra no celular o link de download mostrado ao concluir e instale o APK.

## Verificações
```powershell
cd C:\dev\finance-app\finor-mobile
npm run typecheck
cd C:\dev\finance-app
mvn '-Dmaven.repo.local=C:\dev\finance-app\.m2' -f finor-api/pom.xml test
```
Os testes Java externos/emuladores só executam quando habilitados. A execução comum não valida login Google real nem uma compilação Android.

## Resultado da tentativa neste ambiente

Projeto Android gerado com sucesso. TypeScript aprovado; Java: 21 testes passaram, 11 testes de integrações externas ficaram desabilitados. Authentication Emulator iniciou em 127.0.0.1:9099.

Não foi produzido APK. A compilação local foi interrompida após falhas de acesso a repositórios Google/Maven e restrição de escrita do daemon Kotlin fora do workspace. O script de build usa compilação Kotlin no processo para reduzir essa dependência. Execute os comandos num terminal normal com acesso à internet e SDK Android completo; o build ainda precisa ser validado até o final.

## Correção do erro Reanimated/Ninja no Windows

Erro identificado no log Gradle de 07/11/2026: `:react-native-reanimated:buildCMakeDebug[arm64-v8a][reanimated]`, com `ninja: error: mkdir(.../CSS/interpolation/transforms/operations): No such file or directory`.

O projeto define agora `expo.android.cmakeObjectPathMax=240` por plugin Expo local, preservando a configuração após prebuild. O CMake usa hashes para encurtar os caminhos de objetos. A configuração foi regenerada: 111 objetos Reanimated, maior caminho com 238 caracteres; todos os diretórios puderam ser criados. Isso valida a correção dos caminhos; o APK completo ainda exige uma nova compilação Gradle bem-sucedida.

No Expo instalado neste projeto, `expo prebuild` recria os diretórios nativos por padrão; use `--no-clean` para atualizar sem removê-los. Não é necessário apagar node_modules nem todos os caches para aplicar esta correção.

Execute novamente:
```powershell
cd C:\dev\finance-app\finor-mobile
npx expo run:android --device
```

## Correção de namespace Kotlin

A tentativa seguinte ultrapassou a compilação Reanimated e falhou em `:app:compileDebugKotlin`, com `Unresolved reference R` e `Unresolved reference BuildConfig`. MainActivity/MainApplication declaravam `package com.finor`, enquanto o Gradle gerava essas classes no namespace `com.finor.app`.

As duas classes agora declaram `com.finor.app`. O plugin `plugins/with-android-namespace.js` mantém os pacotes alinhados com `android.package` ao executar prebuild. A configuração foi regenerada e verificada: existe apenas uma classe de cada tipo, ambas com o namespace correto, e o limite CMake continua 240. A compilação completa do APK ainda precisa ser repetida.

## Atualização necessária do CMake/Ninja

A tentativa de 5m29s falhou em `:app:buildCMakeDebug[arm64-v8a]`: `Filename longer than 260 characters` no código gerado do Gesture Handler. O limite 240 não resolve todos os módulos. Esta atualização substitui a configuração anterior: CMake Android SDK **3.31.6** com Ninja **1.12 ou superior** e limite de caminhos CMake 1024.

1. No Android Studio abra Settings > Languages & Frameworks > Android SDK > SDK Tools.
2. Marque Show Package Details, expanda CMake, selecione 3.31.6 e aplique a instalação.
3. Feche builds em execução e execute no PowerShell:
```powershell
cd C:\dev\finance-app
.\scripts\repair-android-native.ps1 -Build
```

O script confere a existência do CMake/Ninja moderno antes do build, aplica o prebuild e remove apenas artefatos nativos gerados afetados. Mantém node_modules, configurações e caches de dependências. Não altera o registro do Windows nem desativa a arquitetura nova.

CMake 3.31.6 ainda não está instalado nesta máquina. A configuração e a sintaxe do script foram verificadas; a compilação completa depende dessa instalação e ainda não foi validada. A correção do namespace Kotlin permanece aplicada.

