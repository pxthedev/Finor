import { useState } from "react";
import { ScrollView, Text, View, Pressable, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  ArrowDownLeft,
  ArrowUpRight,
  CalendarDays,
  ChevronRight,
  Plus,
  Sparkles,
  Target,
  Wallet,
  X,
} from "lucide-react-native";
import { router } from "expo-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, useSession } from "../src/api";
import {
  Button,
  Input,
  LoadingState,
  ErrorState,
  EmptyState,
} from "../src/components";
import { formatMoney, theme } from "../src/theme";
import type { Available, PlanningSummary } from "../src/planning-summary";
import { useScreenActive } from "../src/use-screen-active";
import { invalidateFinance } from "../src/query";
import { ProfileAvatar, useProfilePhoto } from "../src/profile-photo";
type Account = { id: string; name: string; balance: number };
type Transaction = {
  id: string;
  description: string;
  type: string;
  amount: number;
  date: string;
};
type Budget = {
  category_id: string;
  name: string;
  amount: number;
  spent: number;
};
export default function Home() {
  const active = useScreenActive();
  const photo = useProfilePhoto();
  const session = useSession((s) => s.session);
  const month = new Date().toISOString().slice(0, 7);
  const overview = useQuery({
    queryKey: ["home", session?.uid, month],
    queryFn: () =>
      api<{
        profile: { name: string };
        accounts: Account[];
        transactions: Transaction[];
        budgets: Budget[];
        planning: PlanningSummary;
      }>("/dashboard/home?month=" + month),
    enabled: !!session && active,
  });
  const profile = { data: overview.data?.profile };
  const accounts = { data: overview.data?.accounts, error: overview.error };
  const transactions = {
    data: overview.data?.transactions,
    error: overview.error,
  };
  const budgets = { data: overview.data?.budgets };
  const planning = {
    data: overview.data?.planning,
    isPending: overview.isPending,
    error: overview.error,
  };
  return (
    <SafeAreaView edges={["top"]} style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 24 }}>
        <View style={styles.topbar}>
          <View style={{ flex: 1, gap: 6 }}>
            <Text style={styles.wordmark}>
              finor<Text style={{ color: theme.colors.primary }}>.</Text>
            </Text>
            <Text style={styles.muted}>
              Olá, {profile.data?.name || "bem-vindo"}
            </Text>
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Meu perfil"
            onPress={() => router.push("/profile")}
            style={styles.profile}
          >
            <ProfileAvatar
              name={profile.data?.name}
              photo={photo.data?.dataUrl}
              size={44}
            />
          </Pressable>
        </View>
        <View
          style={{
            flexDirection: "row",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <Text style={styles.eyebrow}>VISÃO GERAL</Text>
          <Text style={styles.muted}>
            {new Date().toLocaleDateString("pt-BR", {
              month: "long",
              year: "numeric",
            })}
          </Text>
        </View>
        {planning.isPending ? (
          <LoadingState />
        ) : (
          <AvailableSummary
            value={planning.data?.available ?? undefined}
            needsUpdate={planning.data?.needsUpdate}
            error={planning.error?.message}
            balance={
              accounts.data?.reduce(
                (sum, item) => sum + Number(item.balance),
                0,
              ) ?? 0
            }
          />
        )}
        <View style={styles.actionGrid}>
          {[
            {
              label: "Registrar",
              hint: "Receita ou despesa",
              route: "/transaction",
              Icon: Plus,
            },
            {
              label: "Registrar salário",
              hint: "Novo recebimento",
              route: "/income",
              Icon: CalendarDays,
            },
            {
              label: "Contas previstas",
              hint: "Tudo em dia",
              route: "/recurring",
              Icon: Wallet,
            },
            {
              label: "Minhas metas",
              hint: "Planos em movimento",
              route: "/goals",
              Icon: Target,
            },
          ].map(({ label, hint, route, Icon }) => (
            <Pressable
              key={route}
              accessibilityRole="button"
              onPress={() =>
                router.push(
                  route as "/transaction" | "/income" | "/recurring" | "/goals",
                )
              }
              style={({ pressed }) => [
                styles.actionCard,
                { opacity: pressed ? 0.7 : 1 },
              ]}
            >
              <View style={styles.actionIcon}>
                <Icon
                  size={21}
                  color={theme.colors.primary}
                  strokeWidth={1.8}
                />
              </View>
              <Text style={styles.actionLabel}>{label}</Text>
              <Text style={styles.actionHint}>{hint}</Text>
            </Pressable>
          ))}
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push("/assistant")}
          style={styles.chatCard}
        >
          <View style={styles.actionIcon}>
            <Sparkles size={22} color={theme.colors.primary} />
          </View>
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={styles.actionLabel}>Uma conversa, mais clareza.</Text>
            <Text style={styles.muted}>
              Seu assistente financeiro está aqui.
            </Text>
          </View>
          <ChevronRight size={20} color={theme.colors.primary} />
        </Pressable>
        <BudgetsList items={budgets.data || []} />
        <Text style={styles.sectionTitle}>Suas contas</Text>
        {accounts.error && <ErrorState message={accounts.error.message} />}
        {accounts.data?.map((a) => (
          <View key={a.id} style={styles.accountCard}>
            <View style={styles.actionIcon}>
              <Wallet size={20} color={theme.colors.primary} />
            </View>
            <Text style={[styles.actionLabel, { flex: 1 }]}>{a.name}</Text>
            <Text
              style={{
                color: "#fff",
                fontSize: 17,
                fontWeight: "600",
                fontVariant: ["tabular-nums"],
              }}
            >
              {formatMoney(Number(a.balance))}
            </Text>
          </View>
        ))}
        <AccountForm />
        {transactions.error && (
          <ErrorState message={transactions.error.message} />
        )}
        <TransactionList items={transactions.data || []} />
        <Button
          label="Ver histórico completo"
          variant="secondary"
          onPress={() => router.push("/history")}
        />
      </ScrollView>
    </SafeAreaView>
  );
}
function AvailableSummary({
  value,
  error,
  needsUpdate = false,
  balance,
}: {
  value?: Available;
  error?: string;
  needsUpdate?: boolean;
  balance: number;
}) {
  const [expanded, setExpanded] = useState(false);
  if (!value)
    return (
      <View style={styles.balanceCard}>
        <Text style={styles.eyebrow}>SALDO ATUAL</Text>
        <Text style={{ color: "#fff", fontSize: 38, fontWeight: "700" }}>
          {formatMoney(balance)}
        </Text>
        <Text style={{ color: "#fff", fontSize: 28, fontWeight: "700" }}>
          {needsUpdate
            ? "Seu saldo está atualizado."
            : "Seu dinheiro, do seu jeito."}
        </Text>
        <Text style={[styles.muted, { lineHeight: 22 }]}>
          {needsUpdate
            ? "Registre novos ganhos e acompanhe seus objetivos na área Metas."
            : "Registre seus ganhos e gastos. Em Metas, acompanhe o que está pagando e o que quer conquistar."}
        </Text>
        <Button
          label="Ver minhas metas"
          onPress={() => router.push("/goals")}
        />
        {!!error && <ErrorState message={error} />}
      </View>
    );
  const rows = [
    ["Saldo registrado", value.balance],
    ["Contas pendentes", value.pending],
    ["Reservas protegidas", value.reserves],
    ...(Number(value.essentials) + Number(value.margin) > 0
      ? [
          [
            "Outros valores reservados",
            Number(value.essentials) + Number(value.margin),
          ] as const,
        ]
      : []),
  ] as const;
  return (
    <View style={styles.balanceCard}>
      <View
        style={{
          flexDirection: "row",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Text style={styles.eyebrow}>SALDO ATUAL</Text>
        <ArrowUpRight size={22} color={theme.colors.primary} />
      </View>
      <Text
        style={{
          color: "#fff",
          fontSize: 38,
          fontWeight: "700",
          letterSpacing: -1.5,
          fontVariant: ["tabular-nums"],
        }}
      >
        {formatMoney(balance)}
      </Text>
      <View style={styles.daily}>
        <View style={styles.dot} />
        <Text style={{ color: theme.colors.primary, fontSize: 13 }}>
          Disponível para gastar
        </Text>
        <Text style={{ color: "#fff", fontWeight: "700", marginLeft: "auto" }}>
          {formatMoney(Number(value.available))}
        </Text>
      </View>
      <Text style={styles.actionHint}>
        Sugestão diária: {formatMoney(Number(value.suggestedDaily))} até o
        próximo recebimento.
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded }}
        onPress={() => setExpanded(!expanded)}
        style={{ paddingVertical: 10, minHeight: 44, justifyContent: "center" }}
      >
        <Text style={{ color: theme.colors.secondary, fontSize: 13 }}>
          {expanded ? "Ocultar composição −" : "Como esse valor é calculado +"}
        </Text>
      </Pressable>
      {expanded &&
        rows.map(([label, amount]) => (
          <View
            key={label}
            style={{
              flexDirection: "row",
              justifyContent: "space-between",
              gap: 8,
            }}
          >
            <Text style={styles.muted}>{label}</Text>
            <Text style={{ color: "#fff", fontSize: 13 }}>
              {formatMoney(Number(amount))}
            </Text>
          </View>
        ))}
    </View>
  );
}
function BudgetsList({ items }: { items: Budget[] }) {
  return (
    <>
      <View style={styles.topbar}>
        <Text style={styles.sectionTitle}>Orçamentos</Text>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push("/budgets")}
          style={styles.smallButton}
        >
          <Text style={{ color: theme.colors.primary, fontSize: 13 }}>
            Gerenciar
          </Text>
          <ChevronRight color={theme.colors.primary} size={16} />
        </Pressable>
      </View>
      {!items.length && (
        <EmptyState
          title="Defina seus limites"
          description="Crie categorias e um orçamento mensal em Gerenciar."
        />
      )}
      {items.map((b) => (
        <View key={b.category_id} style={[styles.card, { gap: 10 }]}>
          <Text style={{ color: "#fff" }}>{b.name}</Text>
          <Text style={{ color: "#A1A1A1" }}>
            {formatMoney(Number(b.spent))} / {formatMoney(Number(b.amount))}
          </Text>
          <View
            style={{
              height: 5,
              borderRadius: 3,
              overflow: "hidden",
              backgroundColor: theme.colors.border,
            }}
          >
            <View
              style={{
                height: 5,
                backgroundColor:
                  Number(b.spent) > Number(b.amount) ? "#F2B84B" : "#19C96A",
                width: `${Math.min(100, (Number(b.spent) / Math.max(1, Number(b.amount))) * 100)}%`,
              }}
            />
          </View>
        </View>
      ))}
    </>
  );
}
function TransactionList({ items }: { items: Transaction[] }) {
  const labels: Record<string, string> = {
    INCOME: "Receita",
    EXPENSE: "Despesa",
    TRANSFER: "Transferência",
  };
  return (
    <>
      <Text style={styles.sectionTitle}>Últimas transações</Text>
      {!items.length && (
        <EmptyState
          title="Nenhuma transação registrada"
          description="Seus registros aparecerão aqui."
        />
      )}
      {items.slice(0, 5).map((t) => (
        <View key={t.id} style={[styles.card, { gap: 12 }]}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
            <View style={styles.actionIcon}>
              {t.type === "EXPENSE" ? (
                <ArrowUpRight color={theme.colors.expense} size={20} />
              ) : (
                <ArrowDownLeft color={theme.colors.primary} size={20} />
              )}
            </View>
            <View style={{ flex: 1, gap: 4 }}>
              <Text style={styles.actionLabel}>
                {t.description || labels[t.type]}
              </Text>
              <Text style={styles.actionHint}>
                {labels[t.type]} · {t.date}
              </Text>
            </View>
            <Text
              style={{
                color:
                  t.type === "EXPENSE"
                    ? theme.colors.expense
                    : theme.colors.primary,
                fontWeight: "600",
                fontVariant: ["tabular-nums"],
              }}
            >
              {t.type === "EXPENSE" ? "−" : ""}
              {formatMoney(Number(t.amount))}
            </Text>
          </View>
          <View
            style={{
              flexDirection: "row",
              justifyContent: "flex-end",
              gap: 16,
            }}
          >
            <Pressable
              accessibilityRole="button"
              style={styles.smallButton}
              onPress={() =>
                router.push({
                  pathname: "/transaction",
                  params: { editId: t.id },
                })
              }
            >
              <Text style={{ color: theme.colors.secondary, fontSize: 12 }}>
                Detalhes e edição
              </Text>
              <ChevronRight size={14} color={theme.colors.tertiary} />
            </Pressable>
            <Pressable
              accessibilityRole="button"
              style={styles.smallButton}
              onPress={() =>
                router.push({
                  pathname: "/transaction",
                  params: { deleteId: t.id },
                })
              }
            >
              <Text style={{ color: theme.colors.expense, fontSize: 12 }}>
                Excluir
              </Text>
            </Pressable>
          </View>
        </View>
      ))}
    </>
  );
}
function AccountForm() {
  const [open, setOpen] = useState(false);
  const client = useQueryClient();
  const [name, setName] = useState("");
  const [initial, setInitial] = useState("0");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  async function save() {
    setSaving(true);
    setError("");
    try {
      if (!/^-?\d+([.,]\d{1,2})?$/.test(initial))
        throw new Error("Informe um saldo válido.");
      await api("/accounts", {
        method: "POST",
        body: JSON.stringify({
          name,
          type: "CHECKING",
          openingBalance: initial.replace(",", "."),
        }),
      });
      setName("");
      setInitial("0");
      setOpen(false);
      await invalidateFinance(client);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      setSaving(false);
    }
  }
  if (!open)
    return (
      <Button
        variant="secondary"
        label="+ Adicionar conta"
        onPress={() => setOpen(true)}
      />
    );
  return (
    <View style={[styles.card, { gap: 16 }]}>
      <View style={styles.topbar}>
        <Text style={styles.actionLabel}>Nova conta</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Fechar formulário de conta"
          style={styles.smallButton}
          onPress={() => setOpen(false)}
        >
          <X size={20} color={theme.colors.secondary} />
        </Pressable>
      </View>
      <Input label="Nome da nova conta" value={name} onChangeText={setName} />
      <Input
        label="Saldo atual desta conta"
        value={initial}
        onChangeText={setInitial}
        keyboardType="decimal-pad"
      />
      {!!error && <ErrorState message={error} />}
      <Button label="Criar conta" loading={saving} onPress={save} />
    </View>
  );
}
const styles = StyleSheet.create({
  topbar: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
  },
  wordmark: {
    color: "#fff",
    fontWeight: "800",
    fontSize: 32,
    letterSpacing: -1.4,
  },
  muted: { color: theme.colors.secondary, fontSize: 13 },
  eyebrow: {
    color: theme.colors.secondary,
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 1.7,
  },
  profile: {
    width: 48,
    height: 48,
    borderRadius: 18,
    backgroundColor: theme.colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: "#28543A",
  },
  balanceCard: {
    backgroundColor: "#10261A",
    padding: 24,
    borderRadius: 26,
    borderWidth: 1,
    borderColor: "#24583A",
    gap: 16,
    overflow: "hidden",
  },
  daily: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#183723",
    borderRadius: 14,
    padding: 13,
    flexWrap: "wrap",
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: theme.colors.primary,
  },
  actionGrid: { flexDirection: "row", flexWrap: "wrap", gap: 12 },
  actionCard: {
    width: "47%",
    flexGrow: 1,
    padding: 17,
    backgroundColor: theme.colors.surface,
    borderRadius: 21,
    borderWidth: 1,
    borderColor: theme.colors.border,
    gap: 9,
  },
  actionIcon: {
    width: 41,
    height: 41,
    borderRadius: 14,
    backgroundColor: theme.colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  actionLabel: { color: "#EDF5EF", fontSize: 14, fontWeight: "600" },
  actionHint: { color: theme.colors.tertiary, fontSize: 11, lineHeight: 17 },
  chatCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 13,
    backgroundColor: "#0E1C14",
    padding: 18,
    borderRadius: 21,
    borderWidth: 1,
    borderColor: "#24412D",
  },
  sectionTitle: {
    color: "#fff",
    fontSize: 19,
    fontWeight: "700",
    letterSpacing: -0.3,
  },
  accountCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
    borderRadius: 20,
    padding: 16,
  },
  card: {
    padding: 18,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
    borderRadius: 20,
  },
  smallButton: {
    minHeight: 44,
    minWidth: 44,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 4,
  },
});
