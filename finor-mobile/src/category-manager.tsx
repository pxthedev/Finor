import { useState } from "react";
import { Alert, Pressable, Text, View } from "react-native";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react-native";
import { api } from "./api";
import { Button, ErrorState, Input, LoadingState } from "./components";
import { theme } from "./theme";
import { useScreenActive } from "./use-screen-active";

export type Category = { id: string; name: string };
export function CategoryManager() {
  const active = useScreenActive();
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api<Category[]>("/categories"),
    enabled: active,
  });
  async function refresh() {
    await Promise.all(
      [
        "categories",
        "home",
        "category-report",
        "budgets",
        "history",
        "transactions",
      ].map((key) => client.invalidateQueries({ queryKey: [key] })),
    );
  }
  async function save() {
    if (busy) return;
    setError("");
    if (!name.trim()) {
      setError("Dê um nome à categoria.");
      return;
    }
    setBusy(true);
    try {
      await api(editing ? `/categories/${editing}` : "/categories", {
        method: editing ? "PATCH" : "POST",
        body: JSON.stringify({ name: name.trim() }),
      });
      setName("");
      setEditing(null);
      await refresh();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Não foi possível salvar a categoria.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function remove(category: Category) {
    setBusy(true);
    setError("");
    try {
      await api(`/categories/${category.id}`, { method: "DELETE" });
      if (editing === category.id) {
        setEditing(null);
        setName("");
      }
      await refresh();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Não foi possível excluir a categoria.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <View style={{ gap: 14 }}>
      <Button
        variant="secondary"
        label={open ? "Fechar categorias" : "Criar ou editar categorias"}
        onPress={() => setOpen(!open)}
        disabled={busy}
      />
      {open && (
        <View
          style={{
            padding: 18,
            gap: 16,
            borderWidth: 1,
            borderColor: theme.colors.border,
            borderRadius: 20,
            backgroundColor: theme.colors.surface,
          }}
        >
          <Text style={{ color: "#fff", fontSize: 17, fontWeight: "600" }}>
            Categorias do seu jeito
          </Text>
          <Text
            style={{
              color: theme.colors.secondary,
              lineHeight: 21,
              fontSize: 13,
            }}
          >
            Use as categorias prontas, mude os nomes ou crie as suas.
          </Text>
          <Input
            label={editing ? "Novo nome" : "Nome da categoria"}
            placeholder="Ex.: Academia"
            value={name}
            onChangeText={setName}
            maxLength={80}
            editable={!busy}
          />
          <Button
            label={editing ? "Salvar nome" : "Adicionar categoria"}
            loading={busy}
            onPress={save}
          />
          {!!editing && (
            <Button
              variant="ghost"
              label="Cancelar edição"
              disabled={busy}
              onPress={() => {
                setEditing(null);
                setName("");
              }}
            />
          )}
          {categories.isPending && <LoadingState />}
          {categories.error && (
            <ErrorState message={categories.error.message} />
          )}
          {!!error && <ErrorState message={error} />}
          {categories.data?.map((category) => (
            <View
              key={category.id}
              style={{
                flexDirection: "row",
                alignItems: "center",
                gap: 8,
                borderTopWidth: 1,
                borderColor: theme.colors.border,
                paddingTop: 8,
              }}
            >
              <Text style={{ flex: 1, color: "#EDF5EF", fontSize: 14 }}>
                {category.name}
              </Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Renomear ${category.name}`}
                disabled={busy}
                onPress={() => {
                  setEditing(category.id);
                  setName(category.name);
                  setError("");
                }}
                style={{
                  width: 44,
                  height: 44,
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Pencil color={theme.colors.primary} size={18} />
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Excluir ${category.name}`}
                disabled={busy}
                onPress={() =>
                  Alert.alert(
                    "Excluir categoria?",
                    `Os lançamentos de ${category.name} serão mantidos sem categoria.`,
                    [
                      { text: "Cancelar", style: "cancel" },
                      {
                        text: "Excluir",
                        style: "destructive",
                        onPress: () => {
                          void remove(category);
                        },
                      },
                    ],
                  )
                }
                style={{
                  width: 44,
                  height: 44,
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Trash2 color={theme.colors.expense} size={18} />
              </Pressable>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}
