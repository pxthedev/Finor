import * as Crypto from "expo-crypto";
import { useEffect, useRef, useState } from "react";
import { ScrollView, Text, Pressable } from "react-native";
import { invalidateFinance } from "../src/query";
import { router, useLocalSearchParams } from "expo-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../src/api";
import { Button, Input, ErrorState, LoadingState } from "../src/components";
type Entry = {
  id: string;
  account_id: string;
  destination_account_id: string | null;
  category_id: string | null;
  type: string;
  amount: number;
  date: string;
  description: string | null;
};
export default function Transaction() {
  const { deleteId, editId, proposal, entryType } = useLocalSearchParams<{
    deleteId: string;
    editId: string;
    proposal: string;
    entryType: string;
  }>();
  const client = useQueryClient();
  const accounts = useQuery({
    queryKey: ["accounts"],
    queryFn: () => api<{ id: string; name: string }[]>("/accounts"),
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api<{ id: string; name: string }[]>("/categories"),
  });
  const original = useQuery({
    queryKey: ["transaction", editId],
    queryFn: () => api<Entry>("/transactions/" + editId),
    enabled: !!editId,
  });
  const [account, setAccount] = useState("");
  const [category, setCategory] = useState("");
  const [destination, setDestination] = useState("");
  const [type, setType] = useState("EXPENSE");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [date, setDate] = useState(new Date().toLocaleDateString("en-CA"));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (
      !editId &&
      !proposal &&
      (entryType === "INCOME" || entryType === "EXPENSE")
    )
      setType(entryType);
  }, [entryType, editId, proposal]);
  useEffect(() => {
    if (!account && accounts.data?.length === 1)
      setAccount(accounts.data[0].id);
  }, [account, accounts.data]);
  const request = useRef<{ body: string; key: string } | null>(null);
  useEffect(() => {
    if (original.data) {
      const t = original.data;
      setAccount(t.account_id);
      setDestination(t.destination_account_id || "");
      setCategory(t.category_id || "");
      setType(t.type);
      setAmount(String(t.amount));
      setDescription(t.description || "");
      setDate(t.date);
    }
  }, [original.data]);
  useEffect(() => {
    if (proposal) {
      try {
        const p = JSON.parse(proposal);
        setAccount(p.accountId);
        setCategory(p.categoryId || "");
        setDestination(p.destinationAccountId || "");
        setType(p.type);
        setAmount(String(p.amount));
        setDescription(p.description || "");
        setDate(p.date);
      } catch {
        setError("Proposta inválida.");
      }
    }
  }, [proposal]);
  async function save() {
    setBusy(true);
    setError("");
    try {
      if (deleteId)
        await api("/transactions/" + deleteId, { method: "DELETE" });
      else {
        if (
          !/^\d+(?:[.,]\d{1,2})?$/.test(amount) ||
          Number(amount.replace(",", ".")) <= 0
        )
          throw new Error("Informe um valor válido.");
        let selectedAccount = account;
        if (
          !selectedAccount &&
          !editId &&
          accounts.isSuccess &&
          !accounts.data.length &&
          type !== "TRANSFER"
        ) {
          const created = await api<{ id: string }>("/accounts", {
            method: "POST",
            body: JSON.stringify({
              name: "Conta principal",
              type: "CHECKING",
              openingBalance: "0",
            }),
          });
          selectedAccount = created.id;
          setAccount(created.id);
          await client.invalidateQueries({ queryKey: ["accounts"] });
        }
        if (!selectedAccount) throw new Error("Escolha a conta do lançamento.");
        const body = JSON.stringify({
          accountId: selectedAccount,
          destinationAccountId: type === "TRANSFER" ? destination : null,
          categoryId: type === "TRANSFER" ? null : category || null,
          type,
          amount: amount.replace(",", "."),
          date,
          description,
        });
        if (!request.current || request.current.body !== body)
          request.current = { body, key: Crypto.randomUUID() };
        await api("/transactions" + (editId ? "/" + editId : ""), {
          method: editId ? "PATCH" : "POST",
          headers: { "Idempotency-Key": request.current.key },
          body,
        });
      }
      await invalidateFinance(client);
      router.back();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao registrar.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      contentContainerStyle={{ padding: 24, gap: 20 }}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={{ color: "#fff", fontSize: 24 }}>
        {deleteId
          ? "Excluir esta transação?"
          : editId
            ? "Editar transação"
            : "Nova transação"}
      </Text>
      {accounts.isPending && <LoadingState />}
      {accounts.error && <ErrorState message={accounts.error.message} />}
      {original.error && <ErrorState message={original.error.message} />}
      {!deleteId && (
        <>
          {["EXPENSE", "INCOME", "TRANSFER"].map((t, i) => (
            <Pressable key={t} onPress={() => setType(t)}>
              <Text style={{ color: type === t ? "#19C96A" : "#A1A1A1" }}>
                {["Gasto", "Ganho", "Transferência"][i]}
              </Text>
            </Pressable>
          ))}
          <Text style={{ color: "#fff" }}>
            {type === "INCOME"
              ? "Conta que recebeu o ganho"
              : "Conta do lançamento"}
          </Text>
          {accounts.isSuccess &&
            !accounts.data.length &&
            type !== "TRANSFER" && (
              <Text style={{ color: "#A8B9AF" }}>
                Uma conta principal será criada para seus registros.
              </Text>
            )}
          {accounts.data?.map((a) => (
            <Pressable key={a.id} onPress={() => setAccount(a.id)}>
              <Text style={{ color: account === a.id ? "#19C96A" : "#A1A1A1" }}>
                {a.name}
              </Text>
            </Pressable>
          ))}
          {type === "TRANSFER" ? (
            <>
              <Text style={{ color: "#fff" }}>Conta de destino</Text>
              {accounts.data
                ?.filter((a) => a.id !== account)
                .map((a) => (
                  <Pressable key={a.id} onPress={() => setDestination(a.id)}>
                    <Text
                      style={{
                        color: destination === a.id ? "#19C96A" : "#A1A1A1",
                      }}
                    >
                      {a.name}
                    </Text>
                  </Pressable>
                ))}
            </>
          ) : (
            <>
              <Text style={{ color: "#fff" }}>Categoria</Text>
              {categories.data?.map((c) => (
                <Pressable key={c.id} onPress={() => setCategory(c.id)}>
                  <Text
                    style={{ color: category === c.id ? "#19C96A" : "#A1A1A1" }}
                  >
                    {c.name}
                  </Text>
                </Pressable>
              ))}
            </>
          )}
          <Input
            label="Valor em reais"
            keyboardType="decimal-pad"
            value={amount}
            onChangeText={setAmount}
          />
          <Input
            label="Data (AAAA-MM-DD)"
            value={date}
            onChangeText={setDate}
          />
          <Input
            label="Descrição"
            value={description}
            onChangeText={setDescription}
          />
        </>
      )}
      {!!error && <ErrorState message={error} />}
      <Button
        label={deleteId ? "Confirmar exclusão" : "Salvar transação"}
        loading={busy}
        onPress={save}
      />
      <Button label="Cancelar" onPress={() => router.back()} />
    </ScrollView>
  );
}
