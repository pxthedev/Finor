import { useState } from "react";
import { ScrollView, Text } from "react-native";
import { invalidateFinance } from "../src/query";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../src/api";
import { Button, Input, ErrorState } from "../src/components";
import { formatMoney } from "../src/theme";
export default function Recurring() {
  const client = useQueryClient();
  const bills = useQuery({
    queryKey: ["bills"],
    queryFn: () =>
      api<
        {
          id: string;
          name: string;
          amount: number;
          due_date: string;
          paid: boolean;
          rule_id: string | null;
        }[]
      >("/recurring"),
  });
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function save() {
    setBusy(true);
    setError("");
    try {
      await api("/recurring/rules", {
        method: "POST",
        body: JSON.stringify({
          name,
          amount: amount.replace(",", "."),
          dueDate: date,
          paid: false,
        }),
      });
      await invalidateFinance(client);
      setName("");
      setAmount("");
      setDate("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      setBusy(false);
    }
  }
  async function paid(b: NonNullable<typeof bills.data>[number]) {
    setError("");
    try {
      await api("/recurring/" + b.id, {
        method: "PATCH",
        body: JSON.stringify({
          name: b.name,
          amount: b.amount,
          dueDate: b.due_date,
          paid: !b.paid,
        }),
      });
      await invalidateFinance(client);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao atualizar.");
    }
  }
  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ padding: 24, gap: 20 }}
    >
      <Text style={{ color: "#fff", fontSize: 24 }}>Contas previstas</Text>
      <Text style={{ color: "#A1A1A1" }}>
        Marcar como paga retira a previsão. Registre também a despesa na conta
        para atualizar o saldo.
      </Text>
      {bills.error && <ErrorState message={bills.error.message} />}
      {bills.data?.map((b) => (
        <Button
          key={b.id}
          label={`${b.name} · ${formatMoney(Number(b.amount))} · ${b.paid ? "Paga" : "Pendente"} (${b.due_date})`}
          onPress={() => paid(b)}
        />
      ))}
      <Input label="Nome da conta" value={name} onChangeText={setName} />
      <Input
        label="Valor"
        value={amount}
        onChangeText={setAmount}
        keyboardType="decimal-pad"
      />
      <Input
        label="Vencimento (AAAA-MM-DD)"
        value={date}
        onChangeText={setDate}
      />
      {!!error && <ErrorState message={error} />}
      <Button label="Criar conta mensal" loading={busy} onPress={save} />
    </ScrollView>
  );
}
