import { useRef, useState } from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, CreditCard, PiggyBank, Trash2, X } from "lucide-react-native";
import { api } from "../src/api";
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  LoadingState,
} from "../src/components";
import { formatMoney, theme } from "../src/theme";
import { incomeDateFromInput, planningToday } from "../src/planning-summary";
import { installmentProgress, type Installment } from "../src/installments";
import { useScreenActive } from "../src/use-screen-active";
import { invalidateFinance } from "../src/query";

type Goal = {
  id: string;
  name: string;
  target: number;
  reserved: number;
  deadline: string | null;
};
type Mode = "savings" | "installments";
const displayDate = (date: string) =>
  date.slice(0, 10).split("-").reverse().join("/");
const dateInput = (value: string) => {
  const d = value.replace(/\D/g, "").slice(0, 8);
  return d.length <= 2
    ? d
    : d.length <= 4
      ? `${d.slice(0, 2)}/${d.slice(2)}`
      : `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
};
const money = (value: string, allowZero = false) => {
  if (
    !/^\d+([.,]\d{1,2})?$/.test(value) ||
    Number(value.replace(",", ".")) < (allowZero ? 0 : 0.01)
  )
    throw new Error("Informe um valor válido em reais.");
  return value.replace(",", ".");
};
export default function Goals() {
  const active = useScreenActive();
  const client = useQueryClient();
  const goals = useQuery({
    queryKey: ["goals"],
    queryFn: () => api<Goal[]>("/goals"),
    enabled: active,
  });
  const installments = useQuery({
    queryKey: ["installments"],
    queryFn: () => api<Installment[]>("/installments"),
    enabled: active,
  });
  const accounts = useQuery({
    queryKey: ["accounts"],
    queryFn: () =>
      api<{ id: string; name: string; balance: number }[]>("/accounts"),
    enabled: active,
  });
  const [mode, setMode] = useState<Mode>("savings");
  const [formMode, setFormMode] = useState<Mode>("savings");
  const [formOpen, setFormOpen] = useState(false);
  const [id, setId] = useState("");
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [saved, setSaved] = useState("0");
  const [deadline, setDeadline] = useState("");
  const [total, setTotal] = useState("");
  const [paid, setPaid] = useState("0");
  const [firstDue, setFirstDue] = useState(displayDate(planningToday()));
  const [payment, setPayment] = useState<Installment | null>(null);
  const [account, setAccount] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  function openForm(kind: Mode, item?: Goal | Installment) {
    setFormMode(kind);
    setId(item?.id ?? "");
    setName(item?.name ?? "");
    setError("");
    setMessage("");
    if (kind === "savings") {
      const g = item as Goal | undefined;
      setAmount(g ? String(g.target).replace(".", ",") : "");
      setSaved(g ? String(g.reserved).replace(".", ",") : "0");
      setDeadline(g?.deadline ? displayDate(g.deadline) : "");
    } else {
      const p = item as Installment | undefined;
      setAmount(p ? String(p.installment_amount).replace(".", ",") : "");
      setTotal(p ? String(p.total_installments) : "");
      setPaid(p ? String(p.paid_installments) : "0");
      setFirstDue(displayDate(p?.first_due_date ?? planningToday()));
    }
    setFormOpen(true);
  }
  async function mutate(operation: () => Promise<unknown>, success: string) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await operation();
      await invalidateFinance(client);
      setFormOpen(false);
      setPayment(null);
      setMessage(success);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível salvar.");
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  async function save() {
    let body: object;
    try {
      if (!name.trim()) throw new Error("Dê um nome para acompanhar sua meta.");
      if (formMode === "savings") {
        const date = deadline ? incomeDateFromInput(deadline) : null;
        if (deadline && !date)
          throw new Error("Informe um prazo válido em DD/MM/AAAA.");
        body = {
          name: name.trim(),
          target: money(amount),
          reserved: money(saved, true),
          deadline: date,
        };
      } else {
        const first = incomeDateFromInput(firstDue);
        if (
          !first ||
          !/^\d+$/.test(total) ||
          !/^\d+$/.test(paid) ||
          Number(total) < 1 ||
          Number(total) > 360 ||
          Number(paid) > Number(total)
        )
          throw new Error("Confira o vencimento e a quantidade de parcelas.");
        body = {
          name: name.trim(),
          installmentAmount: money(amount),
          totalInstallments: Number(total),
          paidInstallments: Number(paid),
          firstDueDate: first,
        };
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Confira os campos.");
      return;
    }
    await mutate(
      () =>
        api(
          `/${formMode === "savings" ? "goals" : "installments"}${id ? `/${id}` : ""}`,
          { method: id ? "PATCH" : "POST", body: JSON.stringify(body) },
        ),
      "Meta salva.",
    );
  }
  function remove(kind: Mode, item: Goal | Installment) {
    Alert.alert(
      "Remover acompanhamento?",
      kind === "savings"
        ? "O valor reservado para esta meta ficará disponível novamente. O saldo da conta não será alterado."
        : "Os pagamentos já registrados continuarão no Histórico.",
      [
        { text: "Cancelar", style: "cancel" },
        {
          text: "Remover",
          style: "destructive",
          onPress: () => {
            void mutate(
              () =>
                api(
                  `/${kind === "savings" ? "goals" : "installments"}/${item.id}`,
                  { method: "DELETE" },
                ),
              "Acompanhamento removido.",
            );
          },
        },
      ],
    );
  }
  const activeQuery = mode === "savings" ? goals : installments;
  return (
    <View style={{ flex: 1 }}>
      <ScrollView
        contentContainerStyle={styles.page}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={styles.title}>Uma conquista de cada vez.</Text>
        <Text style={styles.secondary}>
          O que você quer conquistar e o que já está pagando, no mesmo lugar.
        </Text>
        <View style={styles.tabs}>
          {(
            [
              { key: "savings", label: "Juntar dinheiro", Icon: PiggyBank },
              { key: "installments", label: "Parcelados", Icon: CreditCard },
            ] as const
          ).map(({ key, label, Icon }) => (
            <Pressable
              key={key}
              accessibilityRole="tab"
              accessibilityState={{ selected: mode === key }}
              onPress={() => {
                setMode(key);
                setError("");
              }}
              style={[styles.tab, mode === key && styles.selectedTab]}
            >
              <Icon
                size={18}
                color={
                  mode === key ? theme.colors.primary : theme.colors.secondary
                }
              />
              <Text
                style={{
                  color:
                    mode === key
                      ? theme.colors.primary
                      : theme.colors.secondary,
                  fontSize: 13,
                  fontWeight: "600",
                }}
              >
                {label}
              </Text>
            </Pressable>
          ))}
        </View>
        <Button
          label={
            mode === "savings"
              ? "+ Nova meta para juntar"
              : "+ Adicionar compra parcelada"
          }
          onPress={() => openForm(mode)}
          disabled={busy}
        />
        {!!message && (
          <Text
            accessibilityLiveRegion="polite"
            style={{ color: theme.colors.primary }}
          >
            {message}
          </Text>
        )}
        {!!error && !formOpen && !payment && <ErrorState message={error} />}
        {activeQuery.isPending && <LoadingState />}
        {activeQuery.error && (
          <ErrorState message={activeQuery.error.message} />
        )}
        {mode === "savings" && goals.data?.length === 0 && (
          <EmptyState
            title="Seu próximo objetivo começa aqui"
            description="Uma viagem, uma reserva ou uma compra à vista. Defina o valor e acompanhe quanto já juntou."
          />
        )}
        {mode === "installments" && installments.data?.length === 0 && (
          <EmptyState
            title="Suas parcelas sob controle"
            description="Cadastre o que está pagando, informe as parcelas já quitadas e acompanhe quanto falta."
          />
        )}
        {mode === "savings" &&
          goals.data?.map((g) => {
            const progress = Math.min(
                100,
                (Number(g.reserved) / Number(g.target)) * 100,
              ),
              complete = Number(g.reserved) >= Number(g.target);
            return (
              <View key={g.id} style={styles.card}>
                <View style={styles.row}>
                  <View style={styles.icon}>
                    <PiggyBank size={22} color={theme.colors.primary} />
                  </View>
                  <Text style={styles.cardTitle}>{g.name}</Text>
                  <Text style={styles.badge}>
                    {complete ? "Concluída" : `${Math.round(progress)}%`}
                  </Text>
                </View>
                <Text style={styles.amount}>
                  {formatMoney(Number(g.reserved))}
                  <Text style={styles.secondary}>
                    {" "}
                    de {formatMoney(Number(g.target))}
                  </Text>
                </Text>
                <Progress value={progress} />
                <Text style={styles.secondary}>
                  {complete
                    ? "Você alcançou sua meta!"
                    : `Faltam ${formatMoney(Math.max(0, Number(g.target) - Number(g.reserved)))}`}
                  {g.deadline ? ` · até ${displayDate(g.deadline)}` : ""}
                </Text>
                <View style={styles.row}>
                  <View style={{ flex: 1 }}>
                    <Button
                      variant="secondary"
                      label="Atualizar quanto juntei"
                      disabled={busy}
                      onPress={() => openForm("savings", g)}
                    />
                  </View>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Remover ${g.name}`}
                    disabled={busy}
                    onPress={() => remove("savings", g)}
                    style={styles.iconButton}
                  >
                    <Trash2 size={18} color={theme.colors.expense} />
                  </Pressable>
                </View>
              </View>
            );
          })}
        {mode === "installments" &&
          installments.data?.map((p) => {
            const state = installmentProgress(p),
              complete = !state.remaining;
            return (
              <View key={p.id} style={styles.card}>
                <View style={styles.row}>
                  <View style={styles.icon}>
                    <CreditCard size={22} color={theme.colors.primary} />
                  </View>
                  <Text style={styles.cardTitle}>{p.name}</Text>
                  <Text style={styles.badge}>
                    {complete
                      ? "Quitado"
                      : `${p.paid_installments}/${p.total_installments}`}
                  </Text>
                </View>
                <Text style={styles.amount}>
                  {formatMoney(Number(p.installment_amount))}
                  <Text style={styles.secondary}> / mês</Text>
                </Text>
                <Progress value={state.progress} />
                <Text style={styles.secondary}>
                  {p.paid_installments} de {p.total_installments} parcelas pagas
                </Text>
                <Text style={styles.secondary}>
                  {complete
                    ? "Todas as parcelas foram pagas."
                    : `Faltam ${state.remaining} parcelas · ${formatMoney(state.remainingAmount)}`}
                </Text>
                {!!state.nextDueDate && (
                  <Text style={styles.caption}>
                    Próximo vencimento: {displayDate(state.nextDueDate)}
                  </Text>
                )}
                {!complete && (
                  <Button
                    label={`Pagar parcela ${Number(p.paid_installments) + 1}/${p.total_installments}`}
                    disabled={busy}
                    onPress={() => {
                      setPayment(p);
                      setError("");
                      setAccount(
                        accounts.data?.length === 1 ? accounts.data[0].id : "",
                      );
                    }}
                  />
                )}
                <View style={styles.row}>
                  <View style={{ flex: 1 }}>
                    <Button
                      variant="ghost"
                      label="Editar compra"
                      disabled={busy}
                      onPress={() => openForm("installments", p)}
                    />
                  </View>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Remover ${p.name}`}
                    disabled={busy}
                    onPress={() => remove("installments", p)}
                    style={styles.iconButton}
                  >
                    <Trash2 size={18} color={theme.colors.expense} />
                  </Pressable>
                </View>
              </View>
            );
          })}
      </ScrollView>
      <Modal
        visible={formOpen}
        transparent
        animationType="slide"
        onRequestClose={() => {
          if (!busy) setFormOpen(false);
        }}
      >
        <KeyboardAvoidingView behavior="padding" style={styles.overlay}>
          <SafeAreaView edges={["bottom"]} style={styles.sheet}>
            <View style={styles.row}>
              <Text style={styles.sheetTitle}>
                {id ? "Editar" : "Nova"}{" "}
                {formMode === "savings" ? "meta" : "compra parcelada"}
              </Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Fechar formulário"
                disabled={busy}
                onPress={() => setFormOpen(false)}
                style={styles.iconButton}
              >
                <X size={22} color="#fff" />
              </Pressable>
            </View>
            <ScrollView
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={{ gap: 16, paddingBottom: 20 }}
            >
              <Input
                label={
                  formMode === "savings"
                    ? "O que você quer conquistar?"
                    : "O que você está pagando?"
                }
                value={name}
                onChangeText={setName}
                maxLength={120}
                placeholder={
                  formMode === "savings" ? "Ex.: Viagem" : "Ex.: Notebook"
                }
              />
              <Input
                label={
                  formMode === "savings"
                    ? "Quanto quer juntar?"
                    : "Valor de cada parcela"
                }
                value={amount}
                onChangeText={setAmount}
                keyboardType="decimal-pad"
              />
              {formMode === "savings" ? (
                <>
                  <Input
                    label="Quanto já juntou?"
                    value={saved}
                    onChangeText={setSaved}
                    keyboardType="decimal-pad"
                  />
                  <Input
                    label="Prazo (opcional)"
                    placeholder="DD/MM/AAAA"
                    value={deadline}
                    onChangeText={(v) => setDeadline(dateInput(v))}
                    keyboardType="number-pad"
                    maxLength={10}
                  />
                  <Text style={styles.caption}>
                    O valor guardado fica reservado dentro do saldo que você já
                    registrou.
                  </Text>
                </>
              ) : (
                <>
                  <Input
                    label="Quantidade de parcelas"
                    value={total}
                    onChangeText={setTotal}
                    keyboardType="number-pad"
                    maxLength={3}
                  />
                  <Input
                    label="Quantas já foram pagas?"
                    value={paid}
                    onChangeText={setPaid}
                    keyboardType="number-pad"
                    maxLength={3}
                    editable={!id}
                  />
                  <Input
                    label="Vencimento da primeira parcela"
                    placeholder="DD/MM/AAAA"
                    value={firstDue}
                    onChangeText={(v) => setFirstDue(dateInput(v))}
                    keyboardType="number-pad"
                    maxLength={10}
                  />
                  <Text style={styles.caption}>
                    Parcelas que você já pagou antes deste cadastro não serão
                    descontadas novamente. Novos pagamentos atualizam o saldo.
                  </Text>
                </>
              )}
              {!!error && <ErrorState message={error} />}
              <Button
                label={
                  id
                    ? "Salvar alterações"
                    : formMode === "savings"
                      ? "Criar meta"
                      : "Adicionar parcelamento"
                }
                loading={busy}
                onPress={save}
              />
            </ScrollView>
          </SafeAreaView>
        </KeyboardAvoidingView>
      </Modal>
      <Modal
        visible={!!payment}
        transparent
        animationType="slide"
        onRequestClose={() => {
          if (!busy) setPayment(null);
        }}
      >
        <View style={styles.overlay}>
          <SafeAreaView edges={["bottom"]} style={styles.sheet}>
            <View style={styles.row}>
              <Text style={styles.sheetTitle}>Registrar pagamento</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Cancelar pagamento"
                disabled={busy}
                onPress={() => setPayment(null)}
                style={styles.iconButton}
              >
                <X size={22} color="#fff" />
              </Pressable>
            </View>
            <ScrollView contentContainerStyle={{ gap: 18 }}>
              <Text style={styles.secondary}>
                {payment?.name} · Parcela{" "}
                {Number(payment?.paid_installments ?? 0) + 1}/
                {payment?.total_installments}
              </Text>
              <Text style={styles.amount}>
                {formatMoney(Number(payment?.installment_amount ?? 0))}
              </Text>
              <Text style={styles.secondary}>
                O pagamento será registrado como gasto e descontado do saldo.
              </Text>
              {accounts.isPending && <LoadingState />}
              {accounts.error && (
                <ErrorState message={accounts.error.message} />
              )}
              {(accounts.data?.length ?? 0) > 1 && (
                <Text style={styles.secondary}>Qual conta você usou?</Text>
              )}
              {accounts.data?.map((a) => (
                <Pressable
                  key={a.id}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: account === a.id }}
                  onPress={() => setAccount(a.id)}
                  style={[
                    styles.account,
                    account === a.id && { borderColor: theme.colors.primary },
                  ]}
                >
                  <Text style={{ color: "#fff", flex: 1 }}>
                    {a.name} · {formatMoney(Number(a.balance))}
                  </Text>
                  {account === a.id && (
                    <Check color={theme.colors.primary} size={18} />
                  )}
                </Pressable>
              ))}
              {!!error && <ErrorState message={error} />}
              <Button
                label="Confirmar pagamento"
                loading={busy}
                disabled={
                  accounts.isPending ||
                  accounts.isError ||
                  ((accounts.data?.length ?? 0) > 1 && !account)
                }
                onPress={() => {
                  if (payment)
                    void mutate(
                      () =>
                        api(
                          `/installments/${payment.id}/payments/${Number(payment.paid_installments) + 1}`,
                          {
                            method: "POST",
                            body: JSON.stringify({
                              accountId: account || null,
                            }),
                          },
                        ),
                      "Parcela paga e saldo atualizado.",
                    );
                }}
              />
            </ScrollView>
          </SafeAreaView>
        </View>
      </Modal>
    </View>
  );
}
function Progress({ value }: { value: number }) {
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(value) }}
      style={{
        height: 6,
        borderRadius: 4,
        overflow: "hidden",
        backgroundColor: theme.colors.border,
      }}
    >
      <View
        style={{
          height: 6,
          width: `${Math.max(0, value)}%`,
          backgroundColor: theme.colors.primary,
        }}
      />
    </View>
  );
}
const styles = StyleSheet.create({
  page: { padding: 20, gap: 20 },
  title: {
    color: "#fff",
    fontSize: 28,
    fontWeight: "700",
    letterSpacing: -0.7,
  },
  secondary: { color: theme.colors.secondary, fontSize: 13, lineHeight: 21 },
  caption: { color: theme.colors.tertiary, fontSize: 12, lineHeight: 20 },
  tabs: {
    flexDirection: "row",
    backgroundColor: theme.colors.surface,
    borderRadius: 16,
    padding: 4,
    gap: 4,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  tab: {
    flex: 1,
    minHeight: 48,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 8,
    borderRadius: 12,
  },
  selectedTab: { backgroundColor: theme.colors.primarySoft },
  card: {
    padding: 20,
    gap: 15,
    borderRadius: 22,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  row: { flexDirection: "row", alignItems: "center", gap: 12 },
  icon: {
    width: 42,
    height: 42,
    backgroundColor: theme.colors.primarySoft,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  cardTitle: { flex: 1, color: "#fff", fontWeight: "600", fontSize: 17 },
  badge: { color: theme.colors.primary, fontSize: 12, fontWeight: "700" },
  amount: { color: "#fff", fontSize: 25, fontWeight: "700" },
  iconButton: {
    width: 44,
    height: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "#0009" },
  sheet: {
    maxHeight: "90%",
    backgroundColor: theme.colors.surface,
    padding: 20,
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    gap: 16,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  sheetTitle: { flex: 1, color: "#fff", fontSize: 20, fontWeight: "700" },
  account: {
    minHeight: 52,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
});
