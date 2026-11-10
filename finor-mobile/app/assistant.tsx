import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Keyboard,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { router } from "expo-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as SecureStore from "expo-secure-store";
import {
  ArrowUp,
  Check,
  ChevronRight,
  History,
  MessageCircle,
  Plus,
  Sparkles,
  X,
} from "lucide-react-native";
import { api, useSession } from "../src/api";
import { Button, ErrorState } from "../src/components";
import { formatMoney, theme } from "../src/theme";
import { invalidateFinance } from "../src/query";
import { useScreenActive } from "../src/use-screen-active";

type Proposal = {
  accountId: string;
  destinationAccountId: string | null;
  categoryId: string | null;
  type: string;
  amount: number;
  description: string;
  date: string;
};
type Action = { id: string; transaction: Proposal; status?: string };
type Bubble = {
  id: string;
  role: string;
  text: string;
  time?: string;
  action?: Action;
};
type Session = { id: string; created_at: string };
const clock = (date?: string) =>
  date
    ? new Date(date).toLocaleTimeString("pt-BR", {
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";

export default function Assistant() {
  const uid = useSession((s) => s.session?.uid);
  return uid ? <Chat key={uid} uid={uid} /> : null;
}
function Chat({ uid }: { uid: string }) {
  const active = useScreenActive();
  const client = useQueryClient();
  const list = useRef<FlatList<Bubble>>(null);
  const stickToBottom = useRef(true);
  useEffect(() => {
    const listener = Keyboard.addListener("keyboardDidShow", () => {
      requestAnimationFrame(() =>
        list.current?.scrollToEnd({ animated: true }),
      );
    });
    return () => listener.remove();
  }, []);
  const lock = useRef(false);
  const [sessionId, setSession] = useState<string | null>(null);
  const [suggestion, setSuggestion] = useState<{ text: string } | null>(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState("");
  const [error, setError] = useState("");
  const [showHistory, setShowHistory] = useState(false);
  const activeKey = `finor.chat.active.${Array.from(uid)
    .map((c) => c.codePointAt(0)!.toString(16).padStart(6, "0"))
    .join("")}`;
  const sessions = useQuery({
    queryKey: ["chat-sessions", uid],
    queryFn: () => api<Session[]>("/assistant/sessions"),
    enabled: active,
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api<{ id: string; name: string }[]>("/categories"),
    enabled: active,
  });
  useEffect(() => {
    if (ready || !sessions.isSuccess) return;
    let cancelled = false;
    async function restore() {
      let saved: string | null = null;
      try {
        saved = await SecureStore.getItemAsync(activeKey);
      } catch {
        /* Server history remains available if local preferences cannot be read. */
      }
      if (cancelled) return;
      setSession(
        sessions.data?.some((s) => s.id === saved)
          ? saved
          : (sessions.data?.[0]?.id ?? null),
      );
      setReady(true);
    }
    void restore();
    return () => {
      cancelled = true;
    };
  }, [ready, sessions.isSuccess, sessions.data, activeKey]);
  useEffect(() => {
    if (ready && sessionId)
      void SecureStore.setItemAsync(activeKey, sessionId).catch(() => {});
  }, [ready, sessionId, activeKey]);
  const history = useQuery({
    queryKey: ["chat", uid, sessionId],
    enabled: ready && !!sessionId && active,
    queryFn: async (): Promise<Bubble[]> => {
      const { messages, actions } = await api<{
        messages: {
          id: string;
          role: string;
          content: string;
          created_at: string;
        }[];
        actions: {
          id: string;
          payload: string;
          status: string;
          created_at?: string;
        }[];
      }>(`/assistant/sessions/${sessionId}/timeline`);
      const bubbles: Bubble[] = [
        ...messages.map((m) => ({
          id: m.id,
          role: m.role,
          text: m.content,
          time: m.created_at,
        })),
        ...actions.map((a) => ({
          id: `action-${a.id}`,
          role: "assistant",
          time: a.created_at,
          text:
            a.status === "CONFIRMED"
              ? "Transação registrada."
              : a.status === "REJECTED"
                ? "Proposta cancelada."
                : "Confira os dados antes de registrar.",
          action: {
            id: a.id,
            transaction: JSON.parse(a.payload) as Proposal,
            status: a.status,
          },
        })),
      ];
      return bubbles.sort((a, b) => {
        const difference =
          (a.time ? new Date(a.time).getTime() : Number.MAX_SAFE_INTEGER) -
          (b.time ? new Date(b.time).getTime() : Number.MAX_SAFE_INTEGER);
        return difference || Number(!!a.action) - Number(!!b.action);
      });
    },
  });
  const messages = sessionId ? (history.data ?? []) : [];
  const loading = !ready || (!!sessionId && history.isPending);
  async function send(message: string): Promise<boolean> {
    const content = message.trim();
    if (
      !content ||
      content.length > 2000 ||
      lock.current ||
      loading ||
      history.isError
    )
      return false;
    lock.current = true;
    stickToBottom.current = true;
    setBusy(true);
    setSending(content);
    setError("");
    try {
      const result = await api<{
        sessionId: string;
        reply: string;
        action?: Action;
      }>("/assistant/messages", {
        method: "POST",
        body: JSON.stringify({ sessionId, message: content }),
      });
      const now = new Date().toISOString();
      client.setQueryData<Bubble[]>(
        ["chat", uid, result.sessionId],
        [
          ...messages,
          { id: `sent-${now}`, role: "user", text: content, time: now },
          {
            id: `reply-${now}`,
            role: "assistant",
            text: result.reply,
            time: now,
            action: result.action,
          },
        ],
      );
      setSending("");
      setSession(result.sessionId);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["chat", uid, result.sessionId] }),
        client.invalidateQueries({ queryKey: ["chat-sessions", uid] }),
        ...(result.action
          ? [client.invalidateQueries({ queryKey: ["categories"] })]
          : []),
      ]);
      return true;
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Não foi possível enviar. Sua mensagem continua no campo abaixo.",
      );
      return false;
    } finally {
      setSending("");
      setBusy(false);
      lock.current = false;
    }
  }
  async function decide(action: Action, confirm: boolean, edit = false) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      await api(
        `/assistant/actions/${action.id}/${confirm ? "confirm" : "reject"}`,
        { method: "POST" },
      );
      await Promise.all([
        client.invalidateQueries({ queryKey: ["chat", uid, sessionId] }),
        ...(confirm ? [invalidateFinance(client)] : []),
      ]);
      if (edit)
        router.push({
          pathname: "/transaction",
          params: { proposal: JSON.stringify(action.transaction) },
        });
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Não foi possível atualizar a proposta.",
      );
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  function select(id: string | null) {
    if (busy) return;
    setSession(id);
    stickToBottom.current = true;
    setError("");
    setReady(true);
    setShowHistory(false);
  }
  return (
    <SafeAreaView edges={["top"]} style={styles.screen}>
      <View style={styles.header}>
        <View style={styles.avatar}>
          <Sparkles color={theme.colors.primary} size={23} />
        </View>
        <View style={{ flex: 1, gap: 3 }}>
          <Text style={styles.title}>Finor Chat</Text>
          <Text style={styles.subtitle}>Seu assistente financeiro</Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Conversas salvas"
          disabled={busy}
          onPress={() => setShowHistory(true)}
          style={styles.iconButton}
        >
          <History color={theme.colors.secondary} size={22} />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Nova conversa"
          disabled={busy || !ready}
          onPress={() => select(null)}
          style={styles.iconButton}
        >
          <Plus color={theme.colors.primary} size={24} />
        </Pressable>
      </View>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        style={{ flex: 1 }}
      >
        <FlatList
          ref={list}
          data={messages}
          initialNumToRender={16}
          maxToRenderPerBatch={8}
          windowSize={7}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.thread}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          onScroll={({ nativeEvent: e }) => {
            stickToBottom.current =
              e.contentSize.height -
                e.contentOffset.y -
                e.layoutMeasurement.height <
              120;
          }}
          scrollEventThrottle={100}
          onContentSizeChange={() => {
            if (stickToBottom.current)
              list.current?.scrollToEnd({ animated: false });
          }}
          ListHeaderComponent={
            <View style={styles.savedNote}>
              <Text style={styles.caption}>Conversas salvas na sua conta</Text>
            </View>
          }
          ListEmptyComponent={
            loading ? (
              <ActivityIndicator
                color={theme.colors.primary}
                style={{ marginTop: 48 }}
              />
            ) : !history.isError && !sessions.isError ? (
              <View style={styles.welcome}>
                <View style={styles.welcomeIcon}>
                  <MessageCircle size={32} color={theme.colors.primary} />
                </View>
                <Text style={styles.welcomeTitle}>
                  Vamos falar de dinheiro?
                </Text>
                <Text style={styles.welcomeText}>
                  Organize seus gastos, tire dúvidas e planeje seu próximo
                  passo. Estou por aqui.
                </Text>
                {[
                  "Quanto posso gastar hoje?",
                  "Quero registrar uma despesa",
                ].map((prompt) => (
                  <Pressable
                    accessibilityRole="button"
                    key={prompt}
                    disabled={busy || !ready}
                    onPress={() => setSuggestion({ text: prompt })}
                    style={styles.suggestion}
                  >
                    <Text style={{ color: theme.colors.secondary, flex: 1 }}>
                      {prompt}
                    </Text>
                    <ChevronRight size={16} color={theme.colors.primary} />
                  </Pressable>
                ))}
              </View>
            ) : undefined
          }
          renderItem={({ item }) => {
            const mine = item.role === "user";
            return (
              <View style={[styles.bubble, mine ? styles.mine : styles.theirs]}>
                {!mine && <Text style={styles.sender}>FINOR</Text>}
                <Text selectable style={styles.message}>
                  {item.text}
                </Text>
                {item.action && (
                  <View style={styles.proposal}>
                    <Text style={styles.caption}>
                      {item.action.status === "CONFIRMED"
                        ? "TRANSAÇÃO REGISTRADA"
                        : item.action.status === "REJECTED"
                          ? "PROPOSTA CANCELADA"
                          : "PROPOSTA DE TRANSAÇÃO"}
                    </Text>
                    <Text style={styles.proposalAmount}>
                      {formatMoney(Number(item.action.transaction.amount))}
                    </Text>
                    <Text style={styles.message}>
                      {item.action.transaction.description}
                    </Text>
                    <Text style={styles.message}>
                      Categoria:{" "}
                      {categories.data?.find(
                        (c) => c.id === item.action!.transaction.categoryId,
                      )?.name || "Sem categoria"}
                    </Text>
                    <Text style={styles.caption}>
                      {item.action.transaction.type === "EXPENSE"
                        ? "Despesa"
                        : item.action.transaction.type === "INCOME"
                          ? "Receita"
                          : "Transferência"}{" "}
                      · {item.action.transaction.date}
                    </Text>
                    {(!item.action.status ||
                      item.action.status === "PENDING") && (
                      <>
                        <Button
                          label="Confirmar registro"
                          disabled={busy}
                          onPress={() => decide(item.action!, true)}
                        />
                        <View style={{ flexDirection: "row", gap: 8 }}>
                          <View style={{ flex: 1 }}>
                            <Button
                              label="Editar"
                              variant="secondary"
                              disabled={busy}
                              onPress={() => decide(item.action!, false, true)}
                            />
                          </View>
                          <View style={{ flex: 1 }}>
                            <Button
                              label="Cancelar"
                              variant="ghost"
                              disabled={busy}
                              onPress={() => decide(item.action!, false)}
                            />
                          </View>
                        </View>
                      </>
                    )}
                  </View>
                )}
                {!!item.time && (
                  <View style={styles.messageMeta}>
                    <Text style={styles.caption}>{clock(item.time)}</Text>
                    {mine && (
                      <Check
                        size={13}
                        color="#9CCDB0"
                        accessibilityLabel="Mensagem salva"
                      />
                    )}
                  </View>
                )}
              </View>
            );
          }}
          ListFooterComponent={
            sending ? (
              <View style={{ gap: 10 }}>
                <View style={[styles.bubble, styles.mine, { opacity: 0.6 }]}>
                  <Text style={styles.message}>{sending}</Text>
                  <Text style={styles.caption}>Enviando…</Text>
                </View>
                <View style={styles.typing}>
                  <ActivityIndicator
                    size="small"
                    color={theme.colors.primary}
                  />
                  <Text style={styles.caption}>Finor está respondendo…</Text>
                </View>
              </View>
            ) : busy ? (
              <ActivityIndicator color={theme.colors.primary} />
            ) : undefined
          }
        />
        {(!!error || history.isError || sessions.isError) && (
          <View style={{ paddingHorizontal: 16, gap: 4 }}>
            <ErrorState
              message={
                error ||
                history.error?.message ||
                sessions.error?.message ||
                "Não foi possível carregar as conversas."
              }
            />
            {(history.isError || sessions.isError) && (
              <Button
                variant="ghost"
                label="Tentar carregar novamente"
                onPress={() => {
                  void sessions.refetch();
                  if (sessionId) void history.refetch();
                }}
              />
            )}
          </View>
        )}
        <ChatComposer
          suggestion={suggestion}
          resetKey={sessionId}
          busy={busy}
          disabled={loading || history.isError}
          onSend={send}
        />
      </KeyboardAvoidingView>
      <Modal
        visible={showHistory}
        transparent
        animationType="slide"
        onRequestClose={() => setShowHistory(false)}
      >
        <View style={styles.overlay}>
          <SafeAreaView edges={["bottom"]} style={styles.historySheet}>
            <View style={styles.sheetHandle} />
            <View style={styles.historyHeader}>
              <Text style={styles.title}>Suas conversas</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Fechar histórico"
                onPress={() => setShowHistory(false)}
                style={styles.iconButton}
              >
                <X color="#fff" size={22} />
              </Pressable>
            </View>
            <Button
              label="Nova conversa"
              onPress={() => select(null)}
              disabled={busy}
            />
            <FlatList
              data={sessions.data ?? []}
              keyExtractor={(s) => s.id}
              contentContainerStyle={{ paddingVertical: 12 }}
              ListEmptyComponent={
                <Text style={styles.welcomeText}>
                  Suas conversas aparecerão aqui depois da primeira mensagem
                  enviada.
                </Text>
              }
              renderItem={({ item }) => (
                <Pressable
                  accessibilityRole="button"
                  disabled={busy}
                  onPress={() => select(item.id)}
                  style={[
                    styles.historyRow,
                    item.id === sessionId && {
                      backgroundColor: theme.colors.primarySoft,
                    },
                  ]}
                >
                  <MessageCircle size={20} color={theme.colors.primary} />
                  <View style={{ flex: 1, gap: 4 }}>
                    <Text style={styles.message}>Conversa com Finor</Text>
                    <Text style={styles.caption}>
                      {new Date(item.created_at).toLocaleString("pt-BR")}
                    </Text>
                  </View>
                  <ChevronRight size={18} color={theme.colors.tertiary} />
                </Pressable>
              )}
            />
          </SafeAreaView>
        </View>
      </Modal>
    </SafeAreaView>
  );
}
// Keep keystrokes local: typing must not re-render the entire conversation.
function ChatComposer({
  suggestion,
  resetKey,
  busy,
  disabled,
  onSend,
}: {
  suggestion: { text: string } | null;
  resetKey: string | null;
  busy: boolean;
  disabled: boolean;
  onSend: (message: string) => Promise<boolean>;
}) {
  const [text, setText] = useState("");
  useEffect(() => setText(""), [resetKey]);
  useEffect(() => {
    if (suggestion) setText(suggestion.text);
  }, [suggestion]);
  const cannotSend = !text.trim() || busy || disabled;
  return (
    <View style={styles.composer}>
      <TextInput
        accessibilityLabel="Mensagem para o Finor"
        placeholder="Mensagem"
        placeholderTextColor={theme.colors.tertiary}
        selectionColor={theme.colors.primary}
        cursorColor={theme.colors.primary}
        textAlignVertical="top"
        value={text}
        onChangeText={setText}
        multiline
        maxLength={2000}
        editable={!busy}
        style={styles.composerInput}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Enviar mensagem"
        accessibilityState={{ disabled: cannotSend }}
        disabled={cannotSend}
        onPress={async () => {
          if (await onSend(text)) setText("");
        }}
        style={({ pressed }) => [
          styles.send,
          { opacity: cannotSend ? 0.35 : pressed ? 0.7 : 1 },
        ]}
      >
        {busy ? (
          <ActivityIndicator color="#07170E" />
        ) : (
          <ArrowUp size={24} color="#07170E" strokeWidth={2.5} />
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: theme.colors.background },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 14,
    backgroundColor: theme.colors.surface,
    borderBottomWidth: 1,
    borderColor: theme.colors.border,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 17,
    backgroundColor: theme.colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: "#23583A",
  },
  title: { color: "#fff", fontSize: 18, fontWeight: "700" },
  subtitle: { color: theme.colors.secondary, fontSize: 12 },
  iconButton: {
    width: 44,
    height: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  thread: { padding: 16, gap: 12, flexGrow: 1 },
  savedNote: { alignItems: "center", paddingVertical: 8 },
  caption: { color: theme.colors.secondary, fontSize: 11, lineHeight: 16 },
  welcome: {
    alignItems: "center",
    alignSelf: "center",
    maxWidth: 380,
    width: "100%",
    paddingHorizontal: 12,
    paddingVertical: 36,
    gap: 16,
  },
  welcomeIcon: {
    width: 72,
    height: 72,
    borderRadius: 26,
    backgroundColor: theme.colors.primarySoft,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#245039",
  },
  welcomeTitle: {
    fontSize: 23,
    fontWeight: "700",
    color: "#fff",
    textAlign: "center",
  },
  welcomeText: {
    color: theme.colors.secondary,
    fontSize: 14,
    lineHeight: 23,
    textAlign: "center",
  },
  suggestion: {
    width: "100%",
    minHeight: 48,
    padding: 14,
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
    borderRadius: 16,
  },
  bubble: { maxWidth: "88%", padding: 14, borderRadius: 19, gap: 7 },
  mine: {
    alignSelf: "flex-end",
    backgroundColor: "#16482D",
    borderBottomRightRadius: 5,
  },
  theirs: {
    alignSelf: "flex-start",
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: "#1D2E24",
    borderBottomLeftRadius: 5,
  },
  sender: {
    color: theme.colors.primary,
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 1.4,
  },
  message: { color: "#EDF5EF", fontSize: 15, lineHeight: 23 },
  messageMeta: {
    flexDirection: "row",
    justifyContent: "flex-end",
    alignItems: "center",
    gap: 5,
  },
  proposal: {
    paddingTop: 12,
    marginTop: 4,
    borderTopWidth: 1,
    borderColor: theme.colors.border,
    gap: 10,
  },
  proposalAmount: {
    color: theme.colors.primary,
    fontSize: 27,
    fontWeight: "700",
  },
  typing: { flexDirection: "row", gap: 8, alignItems: "center", padding: 8 },
  composer: {
    padding: 12,
    flexDirection: "row",
    gap: 10,
    alignItems: "flex-end",
    backgroundColor: theme.colors.background,
  },
  composerInput: {
    flex: 1,
    minHeight: 50,
    maxHeight: 140,
    borderRadius: 25,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
    color: "#fff",
    paddingHorizontal: 19,
    paddingTop: 14,
    paddingBottom: 14,
    fontSize: 15,
  },
  send: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: theme.colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  overlay: { flex: 1, backgroundColor: "#0009", justifyContent: "flex-end" },
  historySheet: {
    backgroundColor: theme.colors.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    maxHeight: "80%",
    padding: 20,
    gap: 10,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: theme.colors.border,
    alignSelf: "center",
  },
  historyHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  historyRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 15,
    borderRadius: 16,
    borderBottomWidth: 1,
    borderColor: theme.colors.border,
  },
});
