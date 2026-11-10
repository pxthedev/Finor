import { router } from "expo-router";
import { useEffect, useRef, useState } from "react";
import * as Crypto from "expo-crypto";
import { ScrollView, Text, View, Pressable } from "react-native";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, useSession } from "../src/api";
import { Button, Input, ErrorState, LoadingState } from "../src/components";
import { formatMoney } from "../src/theme";
import { usePlanningSummary } from "../src/use-planning-summary";
import { incomeDateFromInput, planningToday } from "../src/planning-summary";
import { invalidateFinance } from "../src/query";
export default function Planning() {
  const client = useQueryClient();
  const uid = useSession((s) => s.session?.uid);
  const accounts = useQuery({
    queryKey: ["accounts"],
    queryFn: () =>
      api<{ id: string; name: string; balance: number }[]>("/accounts"),
    enabled: !!uid,
  });
  const request = useRef<{ body: string; key: string } | null>(null);
  const saving = useRef(false);
  const [account, setAccount] = useState("");
  const [salary, setSalary] = useState("");
  const [next, setNext] = useState("");
  const [dirty, setDirty] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const summary = usePlanningSummary();
  const available = summary.data?.available;
  const schedule = summary.data?.schedule;
  useEffect(() => {
    if (dirty) return;
    if (schedule)
      setNext(schedule.next_income.slice(0, 10).split("-").reverse().join("/"));
  }, [schedule, dirty]);
  useEffect(() => {
    if (!account && accounts.data?.length === 1)
      setAccount(accounts.data[0].id);
  }, [account, accounts.data]);
  const currentBalance =
    accounts.data?.reduce((sum, item) => sum + Number(item.balance), 0) ?? 0;
  const salaryAmount = Number(salary.replace(",", "."));
  const preview =
    (Math.round(currentBalance * 100) + Math.round(salaryAmount * 100)) / 100;
  async function save() {
    if (saving.current) return;
    saving.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const money = (value: string) => {
        if (!/^\d+([.,]\d{1,2})?$/.test(value))
          throw new Error("Informe valores em reais.");
        return value.replace(",", ".");
      };
      const monthlyAmount = money(salary);
      if (Number(monthlyAmount) <= 0)
        throw new Error("Informe sua renda mensal.");
      const incomeDate = incomeDateFromInput(next);
      if (!incomeDate || incomeDate <= planningToday())
        throw new Error(
          "Informe uma data futura válida para o próximo recebimento (DD/MM/AAAA).",
        );
      if ((accounts.data?.length ?? 0) > 1 && !account)
        throw new Error("Escolha a conta que recebeu o salário.");
      const payload = {
        monthlyAmount,
        nextIncome: incomeDate,
        accountId: account || null,
      };
      const body = JSON.stringify(payload);
      if (!request.current || request.current.body !== body)
        request.current = { body, key: Crypto.randomUUID() };
      await api("/planning/salary", {
        method: "POST",
        body: JSON.stringify({ ...payload, receiptId: request.current.key }),
      });
      request.current = null;
      setSalary("");
      setDirty(true);
      await invalidateFinance(client);
      setMessage("Novo recebimento registrado e somado ao saldo.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      setBusy(false);
      saving.current = false;
    }
  }
  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      contentContainerStyle={{ padding: 24, gap: 20 }}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={{ color: "#fff", fontSize: 24 }}>
        Registrar salário recebido
      </Text>
      <View
        style={{
          padding: 20,
          borderRadius: 20,
          backgroundColor: "#10261A",
          borderWidth: 1,
          borderColor: "#24583A",
          gap: 8,
        }}
      >
        <Text style={{ color: "#A8B9AF", fontSize: 13 }}>Saldo atual</Text>
        {accounts.isPending ? (
          <LoadingState />
        ) : (
          <Text style={{ color: "#fff", fontSize: 32, fontWeight: "700" }}>
            {formatMoney(currentBalance)}
          </Text>
        )}
        {!!salary.trim() &&
          salaryAmount > 0 &&
          /^\d+([.,]\d{1,2})?$/.test(salary) &&
          accounts.isSuccess && (
            <Text style={{ color: "#19C96A", lineHeight: 22 }}>
              Após salvar: {formatMoney(preview)}
            </Text>
          )}
      </View>
      {summary.isPending && <LoadingState />}
      {summary.data && !schedule && (
        <Text style={{ color: "#A8B9AF", lineHeight: 22 }}>
          Registre o salário recebido neste mês e a data do próximo. Ele será
          somado ao saldo ao salvar.
        </Text>
      )}
      {summary.data?.needsUpdate && (
        <Text style={{ color: "#F2B84B", lineHeight: 22 }}>
          Atualize a data do próximo recebimento para recalcular os valores.
        </Text>
      )}
      {available && (
        <>
          <Text style={{ color: "#A1A1A1" }}>
            Disponível até o próximo recebimento
          </Text>
          <Text style={{ color: "#19C96A", fontSize: 32 }}>
            {formatMoney(Number(available.available))}
          </Text>
          <Text style={{ color: "#fff" }}>
            Limite diário sugerido:{" "}
            {formatMoney(Number(available.suggestedDaily))}
          </Text>
          <Text style={{ color: "#A1A1A1" }}>
            Contas pendentes: {formatMoney(Number(available.pending))} ·
            Reservas: {formatMoney(Number(available.reserves))}
          </Text>
        </>
      )}
      {summary.error && <ErrorState message={summary.error.message} />}
      {accounts.error && <ErrorState message={accounts.error.message} />}
      <Input
        label="Quanto você recebeu agora?"
        value={salary}
        onChangeText={(value) => {
          setDirty(true);
          setSalary(value);
          setMessage("");
        }}
        keyboardType="decimal-pad"
      />
      {(accounts.data?.length ?? 0) > 1 && (
        <View style={{ gap: 10 }}>
          <Text style={{ color: "#A8B9AF" }}>Conta que recebeu o salário</Text>
          {accounts.data?.map((item) => (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{ checked: account === item.id }}
              key={item.id}
              onPress={() => setAccount(item.id)}
              style={{
                minHeight: 48,
                padding: 14,
                borderRadius: 14,
                backgroundColor: account === item.id ? "#16482D" : "#101815",
              }}
            >
              <Text style={{ color: "#fff" }}>{item.name}</Text>
            </Pressable>
          ))}
        </View>
      )}
      <Text style={{ color: "#A8B9AF", fontSize: 12, lineHeight: 20 }}>
        Cada novo recebimento será somado ao saldo. Para corrigir um lançamento
        anterior, use o Histórico.
      </Text>
      <Input
        label="Quando você recebe de novo?"
        placeholder="DD/MM/AAAA"
        keyboardType="number-pad"
        maxLength={10}
        value={next}
        onChangeText={(value) => {
          const digits = value.replace(/\D/g, "").slice(0, 8);
          setDirty(true);
          setNext(
            digits.length <= 2
              ? digits
              : digits.length <= 4
                ? `${digits.slice(0, 2)}/${digits.slice(2)}`
                : `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`,
          );
          setMessage("");
        }}
      />
      {!!error && <ErrorState message={error} />}
      {!!message && (
        <Text accessibilityLiveRegion="polite" style={{ color: "#19C96A" }}>
          {message}
        </Text>
      )}
      {!!available && (
        <Button
          label="Simular uma compra"
          variant="secondary"
          onPress={() => router.push("/simulation")}
        />
      )}
      <Button
        label="Registrar novo recebimento"
        loading={busy}
        disabled={accounts.isPending || accounts.isError}
        onPress={save}
      />
    </ScrollView>
  );
}
