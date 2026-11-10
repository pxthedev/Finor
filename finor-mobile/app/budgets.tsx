import { useState } from "react";
import { ScrollView, Text, Pressable } from "react-native";
import { invalidateFinance } from "../src/query";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../src/api";
import { Button, Input, ErrorState } from "../src/components";
import { formatMoney } from "../src/theme";
import { CategoryManager } from "../src/category-manager";
export default function Budgets() {
  const client = useQueryClient();
  const [category, setCategory] = useState("");
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const month = new Date().toISOString().slice(0, 7);
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api<{ id: string; name: string }[]>("/categories"),
  });
  const budgets = useQuery({
    queryKey: ["budgets", month],
    queryFn: () =>
      api<
        { category_id: string; name: string; amount: number; spent: number }[]
      >("/budgets?month=" + month),
  });
  async function save() {
    setBusy(true);
    setError("");
    try {
      if (!category || !budgets.data)
        throw new Error("Selecione uma categoria e aguarde os orçamentos.");
      await api("/budgets", {
        method: "POST",
        body: JSON.stringify({
          month,
          allocations: [
            ...budgets.data
              .filter((b) => b.category_id !== category)
              .map((b) => ({ categoryId: b.category_id, amount: b.amount })),
            { categoryId: category, amount: amount.replace(",", ".") },
          ],
        }),
      });
      await invalidateFinance(client);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ padding: 24, gap: 20 }}
    >
      <Text style={{ color: "#fff", fontSize: 24 }}>Orçamentos · {month}</Text>
      {budgets.data?.map((b) => (
        <Text key={b.category_id} style={{ color: "#A1A1A1" }}>
          {b.name}: {formatMoney(Number(b.spent))} /{" "}
          {formatMoney(Number(b.amount))}
        </Text>
      ))}
      {categories.data?.map((c) => (
        <Pressable key={c.id} onPress={() => setCategory(c.id)}>
          <Text style={{ color: category === c.id ? "#19C96A" : "#A1A1A1" }}>
            {c.name}
          </Text>
        </Pressable>
      ))}
      <Input
        label="Limite mensal da categoria"
        value={amount}
        onChangeText={setAmount}
        keyboardType="decimal-pad"
      />
      <Button label="Salvar limite" loading={busy} onPress={save} />
      <CategoryManager />
      {!!error && <ErrorState message={error} />}
      {categories.error && <ErrorState message={categories.error.message} />}
      {budgets.error && <ErrorState message={budgets.error.message} />}
    </ScrollView>
  );
}
