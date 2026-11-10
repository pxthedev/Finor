#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "Configure o arquivo .env de produção antes de iniciar o Finor." >&2
  exit 1
fi
set -a
. ./.env
set +a
if [ -n "${FIRESTORE_EMULATOR_HOST:-}" ] || [ -n "${FIREBASE_AUTH_EMULATOR_HOST:-}" ]; then
  echo "Remova as configurações de emuladores: esta API usará Firebase real." >&2
  exit 1
fi
case "${FIREBASE_PROJECT_ID:-}" in
  ''|demo-*) echo "Configure FIREBASE_PROJECT_ID com o projeto Firebase real." >&2; exit 1 ;;
esac
if [ -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" ] || [ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
  echo "A credencial Admin Firebase não foi encontrada no caminho configurado." >&2
  exit 1
fi
java_version=$(java -XshowSettings:properties -version 2>&1 | awk -F '= ' '/java.specification.version/{print $2; exit}')
case "$java_version" in
  ''|*[!0-9]*) echo "Esta API precisa de Java 21 ou superior." >&2; exit 1 ;;
esac
if [ "$java_version" -lt 21 ]; then
  echo "O runtime Java da hospedagem precisa ser 21 ou superior." >&2
  exit 1
fi
export SPRING_PROFILES_ACTIVE='firestore,prod'
export SERVER_ADDRESS='0.0.0.0'
export SERVER_PORT='8080'
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError}"
exec java -jar app.jar
