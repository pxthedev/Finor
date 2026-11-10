import {
  passwordLogin,
  googleLogin,
  type FirebaseSession,
} from "../src/firebase-auth";
import { queryClient } from "../src/query";
import { useState } from "react";
import { ScrollView, Text, Pressable } from "react-native";
import { router } from "expo-router";
import { useForm, Controller } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Input, ErrorState } from "../src/components";
import { api, useSession } from "../src/api";
const schema = z.object({
  email: z.email("Informe um e-mail válido."),
  password: z.string().min(8, "Use pelo menos 8 caracteres.").max(72),
  name: z.string().max(120),
});
type Form = z.infer<typeof schema>;
export default function Auth() {
  const [googleBusy, setGoogleBusy] = useState(false);
  const [register, setRegister] = useState(false);
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState("");
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "", name: "" },
  });
  async function submit(values: Form) {
    setError("");
    try {
      if (register && !values.name.trim()) throw new Error("Informe seu nome.");
      const session = await passwordLogin(
        values.email,
        values.password,
        register,
      );
      await finish(session, register ? values.name.trim() : undefined);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao conectar.");
    }
  }
  async function finish(session: FirebaseSession, name?: string) {
    try {
      queryClient.clear();
      await useSession.getState().set(session);
      await api("/auth/bootstrap", { method: "POST" });
      if (name)
        await api("/users/me", {
          method: "PATCH",
          body: JSON.stringify({ name }),
        });
      router.replace("/home");
    } catch (e) {
      await useSession.getState().set(null);
      throw e;
    }
  }
  async function google() {
    setError("");
    setGoogleBusy(true);
    try {
      const session = await googleLogin();
      if (session) await finish(session);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Não foi possível entrar com Google.",
      );
    } finally {
      setGoogleBusy(false);
    }
  }
  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      contentContainerStyle={{ padding: 24, gap: 20 }}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={{ color: "#fff", fontSize: 32, fontWeight: "600" }}>
        {register ? "Sua vida financeira começa aqui." : "Bem-vindo de volta."}
      </Text>
      {(register
        ? (["name", "email", "password"] as const)
        : (["email", "password"] as const)
      ).map((name) => (
        <Controller
          key={name}
          control={control}
          name={name}
          render={({ field: { onChange, onBlur, value } }) => (
            <>
              <Input
                label={
                  name === "name"
                    ? "Nome"
                    : name === "email"
                      ? "E-mail"
                      : "Senha"
                }
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                autoCapitalize="none"
                secureTextEntry={name === "password" && !visible}
                keyboardType={name === "email" ? "email-address" : "default"}
              />
              {errors[name] && <ErrorState message={errors[name].message!} />}
            </>
          )}
        />
      ))}
      <Pressable onPress={() => setVisible(!visible)}>
        <Text style={{ color: "#A1A1A1" }}>
          {visible ? "Ocultar senha" : "Mostrar senha"}
        </Text>
      </Pressable>
      {!!error && <ErrorState message={error} />}
      <Button
        label={register ? "Criar conta" : "Entrar"}
        loading={isSubmitting || googleBusy}
        onPress={handleSubmit(submit)}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Continuar com Google"
        disabled={isSubmitting || googleBusy}
        onPress={google}
        style={{
          backgroundColor: "#FFFFFF",
          borderRadius: 12,
          minHeight: 52,
          alignItems: "center",
          justifyContent: "center",
          opacity: isSubmitting || googleBusy ? 0.6 : 1,
        }}
      >
        <Text style={{ color: "#1F1F1F", fontSize: 16, fontWeight: "600" }}>
          {googleBusy ? "Conectando ao Google…" : "Continuar com Google"}
        </Text>
      </Pressable>
      <Pressable onPress={() => setRegister(!register)}>
        <Text style={{ color: "#19C96A", textAlign: "center" }}>
          {register ? "Já tenho uma conta" : "Criar minha conta"}
        </Text>
      </Pressable>
      <Button
        label="Esqueci minha senha"
        variant="ghost"
        onPress={() => router.push("/recover")}
      />
    </ScrollView>
  );
}
