import { useState } from "react";
import { ScrollView, Text } from "react-native";
import { Button, Input, ErrorState } from "../src/components";
import { resetEmail, resetPassword } from "../src/firebase-auth";
export default function Recover() {
  const [email, setEmail] = useState("");
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(reset: boolean) {
    setBusy(true);
    setError("");
    try {
      if (reset) {
        await resetPassword(token, password);
        setMessage("Senha atualizada. Você já pode entrar.");
      } else {
        await resetEmail(email);
        setMessage("Confira seu e-mail e abra o link para redefinir a senha.");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao conectar.");
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
      <Text style={{ color: "#fff", fontSize: 24 }}>Recuperar acesso</Text>
      <Input
        label="E-mail"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
      />
      <Button
        label="Enviar link de recuperação"
        loading={busy}
        onPress={() => submit(false)}
      />
      <Input
        label="Código oobCode do link (opcional)"
        value={token}
        onChangeText={setToken}
        autoCapitalize="none"
      />
      <Input
        label="Nova senha (mínimo 8 caracteres)"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />
      <Button
        label="Atualizar senha"
        loading={busy}
        onPress={() => submit(true)}
      />
      {!!message && <Text style={{ color: "#19C96A" }}>{message}</Text>}
      {!!error && <ErrorState message={error} />}
    </ScrollView>
  );
}
