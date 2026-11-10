import { refreshFirebase, type FirebaseSession } from "./firebase-auth";
import { queryClient } from "./query";
import * as SecureStore from "expo-secure-store";
import { create } from "zustand";
const base = process.env.EXPO_PUBLIC_API_URL;
type Session = FirebaseSession;
export const useSession = create<{
  session: Session | null;
  ready: boolean;
  set: (session: Session | null) => Promise<void>;
  restore: () => Promise<void>;
}>((set) => ({
  session: null,
  ready: false,
  set: async (session) => {
    if (session)
      await SecureStore.setItemAsync(
        "finor.firebase.session.v1",
        JSON.stringify(session),
      );
    else {
      await SecureStore.deleteItemAsync("finor.firebase.session.v1");
      queryClient.clear();
    }
    set({ session, ready: true });
  },
  restore: async () => {
    try {
      const raw = await SecureStore.getItemAsync("finor.firebase.session.v1");
      set({ session: raw ? JSON.parse(raw) : null, ready: true });
    } catch {
      set({ session: null, ready: true });
    }
  },
}));
let refreshing: Promise<void> | null = null;
async function request(url: string, options: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45000);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch {
    throw new Error(
      "Sem conexão com o servidor. Verifique sua internet e tente novamente.",
    );
  } finally {
    clearTimeout(timeout);
  }
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
  retry = true,
): Promise<T> {
  if (!base)
    throw new Error("Configure EXPO_PUBLIC_API_URL para conectar ao servidor.");
  if (!__DEV__ && !base.startsWith("https://"))
    throw new Error("A versão de produção exige uma API com HTTPS.");
  const session = useSession.getState().session;
  const response = await request(base + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(session ? { Authorization: `Bearer ${session.accessToken}` } : {}),
      ...options.headers,
    },
  });
  if (response.status === 401 && session && retry) {
    if (!refreshing)
      refreshing = (async () => {
        const result = await refreshFirebase(session);
        if (
          useSession.getState().session?.refreshToken !== session.refreshToken
        )
          throw new Error("A sessão foi alterada. Tente novamente.");
        await useSession.getState().set(result);
      })().finally(() => {
        refreshing = null;
      });
    await refreshing;
    return api(path, options, false);
  }
  if (!response.ok) {
    let detail = "Não foi possível concluir a operação.";
    try {
      const body = await response.json();
      detail = body.detail || detail;
    } catch {}
    throw new Error(detail);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
