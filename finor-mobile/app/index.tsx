import { ActivityIndicator, View, Text } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Redirect, router } from "expo-router";
import { ArrowUpRight } from "lucide-react-native";
import { useSession } from "../src/api";
import { Button } from "../src/components";
export default function Welcome() {
  const ready = useSession((state) => state.ready);
  const session = useSession((state) => state.session);

  if (!ready)
    return (
      <View
        style={{ flex: 1, justifyContent: "center", backgroundColor: "#000" }}
      >
        <ActivityIndicator color="#19C96A" />
      </View>
    );
  if (session) return <Redirect href="/home" />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: "#000", padding: 24 }}>
      <View
        style={{
          flexDirection: "row",
          alignItems: "center",
          gap: 8,
          marginTop: 24,
        }}
      >
        <ArrowUpRight color="#19C96A" size={28} />
        <Text style={{ color: "#fff", fontSize: 28, fontWeight: "600" }}>
          finor
        </Text>
      </View>
      <View style={{ flex: 1, justifyContent: "center", gap: 24 }}>
        <Text style={{ color: "#19C96A", fontSize: 12, letterSpacing: 2 }}>
          CLAREZA PARA SUAS ESCOLHAS
        </Text>
        <Text
          style={{
            color: "#fff",
            fontSize: 40,
            fontWeight: "600",
            lineHeight: 48,
          }}
        >
          Organize seu dinheiro com inteligência.
        </Text>
        <Text style={{ color: "#A1A1A1", fontSize: 16, lineHeight: 26 }}>
          Suas contas, seus planos e seu próximo passo. Tudo em um só lugar.
        </Text>
      </View>
      <Button label="Começar agora" onPress={() => router.push("/auth")} />
      <Text
        style={{
          color: "#737373",
          textAlign: "center",
          marginVertical: 20,
          fontSize: 12,
        }}
      >
        Versão em desenvolvimento
      </Text>
    </SafeAreaView>
  );
}
