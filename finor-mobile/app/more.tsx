import { passwordLogin, googleLogin } from "../src/firebase-auth";
import { useState } from "react";
import { ScrollView, Text, Share, View, Pressable } from "react-native";
import {
  ChartNoAxesColumn,
  ChevronRight,
  Download,
  ShieldCheck,
  Target,
  UserRound,
  Wallet,
  type LucideIcon,
} from "lucide-react-native";
import { theme } from "../src/theme";
import { router } from "expo-router";
import { useQueryClient } from "@tanstack/react-query";
import { api, useSession } from "../src/api";
import { Button, Input, ErrorState } from "../src/components";
export default function More() {
  const client = useQueryClient();
  const [deleting, setDeleting] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  async function exportData() {
    try {
      const data = await api("/users/me/export");
      await Share.share({
        message: JSON.stringify(data, null, 2),
        title: "Meus dados Finor",
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao exportar.");
    }
  }
  async function remove() {
    try {
      const old = useSession.getState().session;
      if (!old) throw new Error("Entre novamente.");
      const fresh =
        old.provider === "google.com"
          ? await googleLogin()
          : await passwordLogin(old.email, password);
      if (!fresh) return;
      if (fresh.uid !== old.uid)
        throw new Error("Confirme com a mesma conta conectada.");
      await useSession.getState().set(fresh);
      await api("/users/me", {
        method: "DELETE",
        body: JSON.stringify({ confirm: true }),
      });
      await useSession.getState().set(null);
      client.clear();
      router.replace("/auth");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao excluir.");
    }
  }
  return (
    <ScrollView contentContainerStyle={{ padding: 24, gap: 20 }}>
      <Text
        style={{
          color: "#fff",
          fontSize: 29,
          fontWeight: "700",
          letterSpacing: -0.8,
        }}
      >
        Tudo no seu controle.
      </Text>
      <Text
        style={{ color: theme.colors.secondary, fontSize: 14, lineHeight: 22 }}
      >
        Seus planos, sua conta e as preferências do seu espaço financeiro.
      </Text>
      <Text
        style={{
          color: theme.colors.tertiary,
          fontSize: 11,
          letterSpacing: 1.5,
          fontWeight: "700",
        }}
      >
        ORGANIZAR
      </Text>
      <View
        style={{
          borderWidth: 1,
          borderColor: theme.colors.border,
          borderRadius: 22,
          overflow: "hidden",
          backgroundColor: theme.colors.surface,
        }}
      >
        <MenuRow
          Icon={Wallet}
          label="Orçamentos e categorias"
          onPress={() => router.push("/budgets")}
        />
        <MenuRow
          Icon={Target}
          label="Metas financeiras"
          onPress={() => router.push("/goals")}
        />
        <MenuRow
          Icon={Wallet}
          label="Contas previstas"
          onPress={() => router.push("/recurring")}
        />
        <MenuRow
          Icon={ChartNoAxesColumn}
          label="Relatórios"
          onPress={() => router.push("/reports")}
        />
      </View>
      <Text
        style={{
          color: theme.colors.tertiary,
          fontSize: 11,
          letterSpacing: 1.5,
          fontWeight: "700",
        }}
      >
        MINHA CONTA
      </Text>
      <View
        style={{
          borderWidth: 1,
          borderColor: theme.colors.border,
          borderRadius: 22,
          overflow: "hidden",
          backgroundColor: theme.colors.surface,
        }}
      >
        <MenuRow
          Icon={UserRound}
          label="Meu perfil"
          onPress={() => router.push("/profile")}
        />
        <MenuRow
          Icon={ShieldCheck}
          label="Privacidade"
          onPress={() => router.push("/privacy")}
        />
        <MenuRow
          Icon={Download}
          label="Exportar meus dados"
          onPress={exportData}
        />
      </View>
      <Button
        variant="secondary"
        label="Excluir conta"
        onPress={() => setDeleting(!deleting)}
      />
      <Button
        variant="secondary"
        label="Sair da conta"
        onPress={async () => {
          try {
            await api("/auth/logout", {
              method: "POST",
              body: JSON.stringify({
                refreshToken: useSession.getState().session?.refreshToken,
              }),
            });
          } catch {
            /* A sessão local também pode ser encerrada sem conexão. */
          }
          await useSession.getState().set(null);
          client.clear();
          router.replace("/auth");
        }}
      />
      {deleting && (
        <>
          <Text style={{ color: "#F36B6B" }}>
            A exclusão remove permanentemente seus registros. Confirme sua
            identidade para continuar.
          </Text>
          {useSession.getState().session?.provider !== "google.com" && (
            <Input
              label="Senha"
              secureTextEntry
              value={password}
              onChangeText={setPassword}
            />
          )}
          <Button
            variant="secondary"
            label="Confirmar exclusão permanente"
            onPress={remove}
          />
        </>
      )}
      {!!error && <ErrorState message={error} />}
      <Text style={{ color: "#A1A1A1" }}>
        Finor 0.1 · Organização financeira pessoal. Não conecta a bancos e não
        movimenta dinheiro.
      </Text>
    </ScrollView>
  );
}
function MenuRow({
  Icon,
  label,
  onPress,
}: {
  Icon: LucideIcon;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => ({
        flexDirection: "row",
        alignItems: "center",
        gap: 13,
        padding: 18,
        minHeight: 64,
        borderBottomWidth: 1,
        borderColor: theme.colors.border,
        opacity: pressed ? 0.65 : 1,
      })}
    >
      <Icon color={theme.colors.primary} size={20} strokeWidth={1.7} />
      <Text
        style={{ flex: 1, color: "#EDF5EF", fontSize: 14, fontWeight: "500" }}
      >
        {label}
      </Text>
      <ChevronRight size={17} color={theme.colors.tertiary} />
    </Pressable>
  );
}
