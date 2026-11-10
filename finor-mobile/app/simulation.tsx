import { useState } from "react";
import { ScrollView, Text } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { api } from "../src/api";
import { Button, Input, ErrorState } from "../src/components";
import { formatMoney } from "../src/theme";
export default function Simulation() {
  const [amount, setAmount] = useState("");
  const [result, setResult] = useState<{
    remaining: number;
    fitsRegisteredBudget: boolean;
    notice: string;
  }>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function simulate() {
    setBusy(true);
    setError("");
    try {
      setResult(
        await api("/planning/simulate", {
          method: "POST",
          body: JSON.stringify({ amount: amount.replace(",", ".") }),
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao simular.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ padding: 24, gap: 24 }}
    >
      <Text style={{ color: "#fff", fontSize: 24 }}>
        Essa compra cabe no orçamento?
      </Text>
      <Input
        label="Valor da compra"
        value={amount}
        onChangeText={setAmount}
        keyboardType="decimal-pad"
      />
      <Button label="Simular sem registrar" loading={busy} onPress={simulate} />
      {!!error && <ErrorState message={error} />}
      {result && (
        <>
          <Text
            style={{
              color: result.fitsRegisteredBudget ? "#19C96A" : "#F36B6B",
              fontSize: 24,
            }}
          >
            Restariam {formatMoney(Number(result.remaining))}
          </Text>
          <Text style={{ color: "#fff" }}>
            {result.fitsRegisteredBudget
              ? "Cabe no orçamento registrado."
              : "Supera o disponível registrado."}
          </Text>
          <Text style={{ color: "#A1A1A1" }}>{result.notice}</Text>
        </>
      )}
    </ScrollView>
  );
}
