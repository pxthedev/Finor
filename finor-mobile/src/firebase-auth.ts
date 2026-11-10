import { firebaseConfig } from "./lib/firebase";
import { Platform } from "react-native";
export type FirebaseSession = {
  accessToken: string;
  refreshToken: string;
  uid: string;
  email: string;
  provider: "password" | "google.com";
};
const key = process.env.EXPO_PUBLIC_FIREBASE_API_KEY || firebaseConfig.apiKey;
async function call(path: string, body: object, refresh = false): Promise<any> {
  if (!key) throw new Error("Configure a chave Web do Firebase.");
  const emulator = process.env.EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL;
  if (emulator && !__DEV__)
    throw new Error("Emulador indisponível em produção.");
  const host = refresh
    ? "securetoken.googleapis.com"
    : "identitytoolkit.googleapis.com";
  const url = emulator
    ? `${emulator}/${host}/v1/${path}?key=${encodeURIComponent(key)}`
    : `https://${host}/v1/${path}?key=${encodeURIComponent(key)}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": refresh
        ? "application/x-www-form-urlencoded"
        : "application/json",
    },
    body: refresh
      ? new URLSearchParams(body as Record<string, string>).toString()
      : JSON.stringify(body),
    signal: AbortSignal.timeout(30000),
  });
  const data = await response.json();
  if (!response.ok) {
    const code = data.error?.message?.split(" : ")[0];
    const errors: Record<string, string> = {
      EMAIL_EXISTS: "Este e-mail já está cadastrado.",
      INVALID_LOGIN_CREDENTIALS: "E-mail ou senha incorretos.",
      INVALID_PASSWORD: "E-mail ou senha incorretos.",
      EMAIL_NOT_FOUND: "E-mail ou senha incorretos.",
      TOO_MANY_ATTEMPTS_TRY_LATER:
        "Muitas tentativas. Aguarde e tente novamente.",
      OPERATION_NOT_ALLOWED:
        "Ative este método de acesso no Firebase Authentication.",
      FEDERATED_USER_ID_ALREADY_LINKED: "Esta conta Google já está vinculada.",
      NEED_CONFIRMATION:
        "Este e-mail já possui outro método de acesso. Entre com ele primeiro.",
    };
    throw new Error(
      errors[code] ||
        "Não foi possível autenticar no Firebase. Tente novamente.",
    );
  }
  if (data.needConfirmation)
    throw new Error("Entre com o método já cadastrado para este e-mail.");
  return data;
}
function session(
  data: any,
  provider: FirebaseSession["provider"],
): FirebaseSession {
  return {
    accessToken: data.idToken,
    refreshToken: data.refreshToken,
    uid: data.localId,
    email: data.email || "",
    provider,
  };
}
export async function passwordLogin(
  email: string,
  password: string,
  register = false,
) {
  if (register && password.length < 8)
    throw new Error("Use pelo menos 8 caracteres.");
  return session(
    await call(register ? "accounts:signUp" : "accounts:signInWithPassword", {
      email,
      password,
      returnSecureToken: true,
    }),
    "password",
  );
}
export async function refreshFirebase(old: FirebaseSession) {
  const d = await call(
    "token",
    { grant_type: "refresh_token", refresh_token: old.refreshToken },
    true,
  );
  return { ...old, accessToken: d.id_token, refreshToken: d.refresh_token };
}
export async function googleLogin(): Promise<FirebaseSession | null> {
  const webClientId = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  if (!webClientId)
    throw new Error(
      "Configure EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID para entrar com Google.",
    );
  if (Platform.OS === "web")
    throw new Error(
      "O acesso com Google está disponível no aplicativo Android e iOS.",
    );
  if (Platform.OS === "ios" && !process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID)
    throw new Error(
      "O acesso com Google no iPhone ainda precisa ser configurado. Entre com seu e-mail e senha.",
    );
  let sdk: typeof import("@react-native-google-signin/google-signin");
  try {
    sdk = await import("@react-native-google-signin/google-signin");
  } catch {
    throw new Error(
      "O acesso com Google exige um build nativo. Não funciona no Expo Go.",
    );
  }
  sdk.GoogleSignin.configure({
    webClientId,
    iosClientId: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID,
  });
  try {
    if (Platform.OS === "android")
      await sdk.GoogleSignin.hasPlayServices({
        showPlayServicesUpdateDialog: true,
      });
    await sdk.GoogleSignin.signOut();
    const result = await sdk.GoogleSignin.signIn();
    if (result.type !== "success") return null;
    if (!result.data.idToken)
      throw new Error("O Google não retornou um token. Confira os IDs OAuth.");
    return session(
      await call("accounts:signInWithIdp", {
        postBody: new URLSearchParams({
          id_token: result.data.idToken,
          providerId: "google.com",
        }).toString(),
        requestUri: "http://localhost",
        returnSecureToken: true,
        returnIdpCredential: true,
      }),
      "google.com",
    );
  } catch (e) {
    if (sdk.isErrorWithCode(e) && e.code === sdk.statusCodes.SIGN_IN_CANCELLED)
      return null;
    throw e;
  }
}
export async function resetEmail(email: string) {
  await call("accounts:sendOobCode", { requestType: "PASSWORD_RESET", email });
}
export async function resetPassword(oobCode: string, newPassword: string) {
  if (newPassword.length < 8) throw new Error("Use pelo menos 8 caracteres.");
  await call("accounts:resetPassword", { oobCode, newPassword });
}
