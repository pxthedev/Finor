const standaloneProfiles = new Set([
  "preview",
  "production",
  "ios-simulator",
  "ios-unsigned",
]);

function validateBuildEnvironment(env) {
  const profile = env.EAS_BUILD_PROFILE;
  if (!standaloneProfiles.has(profile)) return;
  let api;
  try {
    api = new URL(env.EXPO_PUBLIC_API_URL);
  } catch {}
  if (
    !api ||
    api.protocol !== "https:" ||
    ["localhost", "127.0.0.1", "[::1]", "0.0.0.0"].includes(api.hostname)
  ) {
    throw new Error(
      "Configure EXPO_PUBLIC_API_URL com a URL HTTPS pública da API no ambiente EAS antes de gerar o app Android/iOS.",
    );
  }
  if (
    env.EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL ||
    env.EXPO_PUBLIC_FIREBASE_API_KEY === "demo-key"
  ) {
    throw new Error(
      "Os builds instaláveis Android/iOS exigem Firebase real. Remova a configuração do emulador do ambiente EAS.",
    );
  }
  if (env.FINOR_ALLOW_LOCAL_NETWORK === "1") {
    throw new Error(
      "FINOR_ALLOW_LOCAL_NETWORK é exclusivo dos builds de desenvolvimento.",
    );
  }
}

function googleIosScheme(clientId) {
  if (!clientId) return undefined;
  if (!/^[A-Za-z0-9-]+\.apps\.googleusercontent\.com$/.test(clientId)) {
    throw new Error(
      "EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID deve ser o cliente OAuth iOS completo, terminado em .apps.googleusercontent.com.",
    );
  }
  return clientId.split(".").reverse().join(".");
}

module.exports = { validateBuildEnvironment, googleIosScheme };
