import { useEffect, useState } from "react";
import { useSession } from "../src/api";
import { queryClient } from "../src/query";
import { Stack, usePathname, router } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { QueryClientProvider, focusManager } from "@tanstack/react-query";
import { theme } from "../src/theme";
import { View, Keyboard, AppState, Platform } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { BottomNavigation } from "../src/bottom-navigation";
const client = queryClient;
export default function Layout() {
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) =>
      focusManager.setFocused(state === "active"),
    );
    return () => subscription.remove();
  }, []);
  const [keyboardVisible, setKeyboardVisible] = useState(Keyboard.isVisible());
  useEffect(() => {
    const shown = Keyboard.addListener(
      Platform.OS === "ios" ? "keyboardWillShow" : "keyboardDidShow",
      (event) => {
        if (Platform.OS === "ios") Keyboard.scheduleLayoutAnimation(event);
        setKeyboardVisible(true);
      },
    );
    const hidden = Keyboard.addListener(
      Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide",
      (event) => {
        if (Platform.OS === "ios") Keyboard.scheduleLayoutAnimation(event);
        setKeyboardVisible(false);
      },
    );
    return () => {
      shown.remove();
      hidden.remove();
    };
  }, []);
  const path = usePathname();
  const { ready, session, restore } = useSession();
  useEffect(() => {
    restore();
  }, []);
  useEffect(() => {
    if (
      ready &&
      !session &&
      !["/", "/auth", "/recover", "/privacy"].includes(path)
    )
      router.replace("/auth");
  }, [ready, session, path]);
  const show =
    !!session && !["/", "/auth", "/foundation", "/recover"].includes(path);
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={client}>
        <StatusBar style="light" />
        <View style={{ flex: 1, backgroundColor: theme.colors.background }}>
          <Stack
            screenOptions={{
              contentStyle: { backgroundColor: theme.colors.background },
              headerStyle: { backgroundColor: theme.colors.background },
              headerTintColor: "#fff",
              headerTitleStyle: { fontSize: 17, fontWeight: "600" },
              headerBackButtonDisplayMode: "minimal",
              headerShadowVisible: false,
            }}
          >
            <Stack.Screen name="index" options={{ headerShown: false }} />
            <Stack.Screen name="home" options={{ headerShown: false }} />
            <Stack.Screen name="assistant" options={{ headerShown: false }} />
            <Stack.Screen name="auth" options={{ title: "Sua conta Finor" }} />
            <Stack.Screen name="planning" options={{ headerShown: false }} />
            <Stack.Screen
              name="income"
              options={{ title: "Registrar salário" }}
            />
            <Stack.Screen name="reports" options={{ title: "Relatórios" }} />
            <Stack.Screen name="more" options={{ title: "Seu espaço" }} />
            <Stack.Screen
              name="transaction"
              options={{ title: "Movimentação" }}
            />
            <Stack.Screen
              name="goals"
              options={{ title: "Metas financeiras" }}
            />
            <Stack.Screen
              name="recurring"
              options={{ title: "Contas previstas" }}
            />
            <Stack.Screen name="profile" options={{ title: "Meu perfil" }} />
            <Stack.Screen name="budgets" options={{ title: "Orçamentos" }} />
            <Stack.Screen name="history" options={{ title: "Histórico" }} />
            <Stack.Screen
              name="recover"
              options={{ title: "Recuperar acesso" }}
            />
            <Stack.Screen name="privacy" options={{ title: "Privacidade" }} />
            <Stack.Screen
              name="simulation"
              options={{ title: "Simular compra" }}
            />
          </Stack>
          {show && !keyboardVisible && <BottomNavigation path={path} />}
        </View>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
