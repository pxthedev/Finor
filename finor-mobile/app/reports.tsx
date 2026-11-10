import { router } from "expo-router";
import { useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { api } from "../src/api";
import { Button, Input, ErrorState, LoadingState } from "../src/components";
import { formatMoney } from "../src/theme";
import { CategoryManager } from "../src/category-manager";
import { ExpensePie, type ExpenseCategory } from "../src/expense-pie";
import { useScreenActive } from "../src/use-screen-active";
export default function Reports() {
  const active = useScreenActive();
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const valid = /^\d{4}-(0[1-9]|1[0-2])$/.test(month);
  const overview = useQuery({
    queryKey: ["category-report", "overview", month],
    queryFn: () =>
      api<{
        summary: { income: number; expenses: number };
        categories: ExpenseCategory[];
        allTimeExpenses: ExpenseCategory[];
        evolution: { month: string; income: number; expenses: number }[];
      }>("/reports/overview?month=" + encodeURIComponent(month)),
    enabled: valid && active,
  });
  const summary = overview.data?.summary;
  const categories = overview.data?.categories;
  const maxSpent = Math.max(
    1,
    ...(categories?.map((c) => Number(c.spent)) ?? []),
  );
  return (
    <ScrollView
      contentContainerStyle={{ padding: 24, gap: 24 }}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={{ color: "#fff", fontSize: 24 }}>Gastos</Text>
      <Button
        variant="secondary"
        label="Registrar salário recebido"
        onPress={() => router.push("/income")}
      />
      <View style={{ flexDirection: "row", gap: 10 }}>
        <View style={{ flex: 1 }}>
          <Button
            label="Registrar gasto"
            onPress={() =>
              router.push({
                pathname: "/transaction",
                params: { entryType: "EXPENSE" },
              })
            }
          />
        </View>
        <View style={{ flex: 1 }}>
          <Button
            variant="secondary"
            label="Registrar ganho"
            onPress={() =>
              router.push({
                pathname: "/transaction",
                params: { entryType: "INCOME" },
              })
            }
          />
        </View>
      </View>
      {valid && overview.isPending && <LoadingState />}
      {overview.error && <ErrorState message={overview.error.message} />}
      {overview.data && (
        <ExpensePie categories={overview.data.allTimeExpenses} />
      )}
      <Text style={{ color: "#fff", fontSize: 20 }}>Resumo mensal</Text>
      <Input label="Mês (AAAA-MM)" value={month} onChangeText={setMonth} />
      {!valid && <ErrorState message="Use o formato AAAA-MM." />}
      {summary && (
        <>
          {(["income", "expenses"] as const).map((key, i) => (
            <View key={key} style={{ gap: 12 }}>
              <Text style={{ color: "#A1A1A1" }}>
                {i ? "Despesas" : "Receitas"}
              </Text>
              <Text style={{ color: i ? "#F36B6B" : "#19C96A", fontSize: 32 }}>
                {formatMoney(Number(summary[key]))}
              </Text>
              <View style={{ height: 8, backgroundColor: "#191919" }}>
                <View
                  style={{
                    height: 8,
                    backgroundColor: i ? "#F36B6B" : "#19C96A",
                    width: `${(Number(summary[key]) / Math.max(1, Number(summary.income), Number(summary.expenses))) * 100}%`,
                  }}
                />
              </View>
            </View>
          ))}
        </>
      )}
      <Text style={{ color: "#fff", fontSize: 20 }}>Categorias</Text>
      <CategoryManager />
      {categories
        ?.filter((c) => Number(c.spent) > 0)
        .map((c) => (
          <View key={c.id} style={{ gap: 8 }}>
            <Button
              variant="secondary"
              label="Ver transações desta categoria"
              onPress={() =>
                router.push({
                  pathname: "/history",
                  params: { categoryId: c.id, month },
                })
              }
            />
            <Text style={{ color: "#A1A1A1" }}>
              {c.name} · {formatMoney(Number(c.spent))}
            </Text>
            <View style={{ height: 4, backgroundColor: "#292929" }}>
              <View
                style={{
                  height: 4,
                  backgroundColor: "#19C96A",
                  width: `${(Number(c.spent) / maxSpent) * 100}%`,
                }}
              />
            </View>
          </View>
        ))}
      {categories && !categories.some((c) => Number(c.spent) > 0) && (
        <Text style={{ color: "#A8B9AF", lineHeight: 22 }}>
          Ao registrar um gasto e escolher uma categoria, ele aparece aqui.
        </Text>
      )}
      <Text style={{ color: "#fff", fontSize: 20 }}>Últimos seis meses</Text>
      {overview.data?.evolution.map((m) => (
        <View
          key={m.month}
          style={{
            paddingVertical: 12,
            borderBottomWidth: 1,
            borderColor: "#292929",
            gap: 8,
          }}
        >
          <Text style={{ color: "#fff" }}>{m.month}</Text>
          <Text style={{ color: "#A1A1A1" }}>
            Entradas {formatMoney(Number(m.income))} · Saídas{" "}
            {formatMoney(Number(m.expenses))}
          </Text>
        </View>
      ))}
    </ScrollView>
  );
}
