import { useEffect, useState } from "react";
import { Pressable, ScrollView, Text, View, StyleSheet } from "react-native";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Camera, Check, Mail, ShieldCheck } from "lucide-react-native";
import { api, useSession } from "../src/api";
import { Button, Input, ErrorState, LoadingState } from "../src/components";
import { theme } from "../src/theme";
import {
  ProfileAvatar,
  profilePhotoKey,
  useProfilePhoto,
  type ProfilePhoto,
} from "../src/profile-photo";

export default function Profile() {
  const client = useQueryClient();
  const uid = useSession((s) => s.session?.uid);
  const profile = useQuery({
    queryKey: ["profile"],
    queryFn: () => api<{ name: string; email: string }>("/users/me"),
  });
  const photo = useProfilePhoto();
  const [name, setName] = useState("");
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState<"name" | "photo" | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    if (!dirty && profile.data) setName(profile.data.name);
  }, [profile.data, dirty]);

  async function savePhoto(remove = false) {
    if (busy) return;
    setBusy("photo");
    setError("");
    setMessage("");
    try {
      if (remove) {
        await api("/users/me/photo", { method: "DELETE" });
        client.setQueryData<ProfilePhoto>(profilePhotoKey(uid), {
          dataUrl: null,
        });
        setMessage("Foto removida.");
        return;
      }
      const [picker, manipulator] = await Promise.all([
        import("expo-image-picker"),
        import("expo-image-manipulator"),
      ]);
      const result = await picker.launchImageLibraryAsync({
        mediaTypes: ["images"],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 1,
        exif: false,
      });
      if (result.canceled || !result.assets[0]) return;
      const asset = result.assets[0];
      const context = manipulator.ImageManipulator.manipulate(asset.uri);
      try {
        const side = Math.min(asset.width, asset.height);
        context
          .crop({
            originX: Math.floor((asset.width - side) / 2),
            originY: Math.floor((asset.height - side) / 2),
            width: side,
            height: side,
          })
          .resize({ width: 320, height: 320 });
        const rendered = await context.renderAsync();
        try {
          const image = await rendered.saveAsync({
            compress: 0.8,
            format: manipulator.SaveFormat.JPEG,
            base64: true,
          });
          if (!image.base64)
            throw new Error(
              "Não foi possível preparar essa foto. Escolha outra imagem.",
            );
          const saved = await api<ProfilePhoto>("/users/me/photo", {
            method: "PUT",
            body: JSON.stringify({
              dataUrl: `data:image/jpeg;base64,${image.base64}`,
            }),
          });
          client.setQueryData(profilePhotoKey(uid), saved);
          setMessage("Foto de perfil atualizada.");
        } finally {
          rendered.release();
        }
      } finally {
        context.release();
      }
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Não foi possível atualizar sua foto.",
      );
    } finally {
      setBusy(null);
    }
  }

  async function saveName() {
    if (busy) return;
    setError("");
    setMessage("");
    if (!name.trim() || name.trim().length > 120) {
      setError("Informe um nome de até 120 caracteres.");
      return;
    }
    setBusy("name");
    try {
      await api("/users/me", {
        method: "PATCH",
        body: JSON.stringify({ name: name.trim() }),
      });
      client.setQueryData(["profile"], { ...profile.data, name: name.trim() });
      await client.invalidateQueries({ queryKey: ["home"] });
      setDirty(false);
      setMessage("Nome atualizado.");
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Não foi possível salvar seu nome.",
      );
    } finally {
      setBusy(null);
    }
  }

  return (
    <ScrollView
      automaticallyAdjustKeyboardInsets
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <View style={styles.hero}>
        <Text style={styles.eyebrow}>SEU ESPAÇO</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Escolher foto de perfil"
          disabled={!!busy}
          onPress={() => savePhoto()}
          style={({ pressed }) => [
            styles.avatarRing,
            { opacity: pressed || busy === "photo" ? 0.7 : 1 },
          ]}
        >
          <ProfileAvatar
            name={profile.data?.name}
            photo={photo.data?.dataUrl}
            size={104}
          />
          <View style={styles.camera}>
            <Camera size={17} color="#072214" strokeWidth={2.3} />
          </View>
        </Pressable>
        <View style={{ alignItems: "center", gap: 6 }}>
          <Text style={styles.name}>{profile.data?.name || "Meu perfil"}</Text>
          <View style={styles.email}>
            <Mail size={14} color={theme.colors.tertiary} />
            <Text style={styles.secondary}>{profile.data?.email}</Text>
          </View>
        </View>
        <View style={{ width: "100%", gap: 4 }}>
          <Button
            label={photo.data?.dataUrl ? "Trocar foto" : "Adicionar foto"}
            variant="secondary"
            loading={busy === "photo"}
            disabled={!!busy}
            onPress={() => savePhoto()}
          />
          {!!photo.data?.dataUrl && (
            <Button
              label="Remover foto"
              variant="ghost"
              disabled={!!busy}
              onPress={() => savePhoto(true)}
            />
          )}
        </View>
      </View>
      {profile.isPending && <LoadingState />}
      {(profile.error || photo.error || error) && (
        <ErrorState
          message={
            error ||
            profile.error?.message ||
            photo.error?.message ||
            "Não foi possível carregar seu perfil."
          }
        />
      )}
      {!!message && (
        <View accessibilityLiveRegion="polite" style={styles.success}>
          <Check size={18} color={theme.colors.primary} />
          <Text style={{ color: theme.colors.primary, flex: 1 }}>
            {message}
          </Text>
        </View>
      )}
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Informações pessoais</Text>
        <Input
          label="Como você quer ser chamado?"
          value={name}
          maxLength={120}
          editable={!busy}
          autoCapitalize="words"
          onChangeText={(value) => {
            setDirty(true);
            setName(value);
          }}
        />
        <Button
          label="Salvar alterações"
          loading={busy === "name"}
          disabled={!!busy || !dirty || !name.trim()}
          onPress={saveName}
        />
      </View>
      <View style={styles.note}>
        <ShieldCheck size={18} color={theme.colors.tertiary} />
        <Text style={[styles.secondary, { flex: 1, lineHeight: 20 }]}>
          Sua foto fica vinculada à sua conta. Você pode trocar ou remover
          quando quiser.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    padding: 20,
    gap: 20,
    width: "100%",
    maxWidth: 620,
    alignSelf: "center",
  },
  hero: {
    alignItems: "center",
    padding: 24,
    gap: 20,
    borderRadius: 28,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  eyebrow: {
    color: theme.colors.tertiary,
    fontSize: 10,
    letterSpacing: 2,
    fontWeight: "700",
  },
  avatarRing: {
    padding: 5,
    borderWidth: 1,
    borderColor: "#2B6645",
    borderRadius: 60,
  },
  camera: {
    position: "absolute",
    right: 0,
    bottom: 0,
    height: 32,
    width: 32,
    borderRadius: 16,
    backgroundColor: theme.colors.primary,
    borderWidth: 3,
    borderColor: theme.colors.surface,
    alignItems: "center",
    justifyContent: "center",
  },
  name: {
    color: theme.colors.text,
    fontSize: 24,
    fontWeight: "700",
    textAlign: "center",
  },
  email: {
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    flexWrap: "wrap",
    justifyContent: "center",
  },
  secondary: { color: theme.colors.secondary, fontSize: 13 },
  sectionTitle: { color: theme.colors.text, fontSize: 17, fontWeight: "600" },
  card: {
    padding: 20,
    gap: 20,
    borderRadius: 24,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  success: {
    backgroundColor: theme.colors.primarySoft,
    borderRadius: 16,
    padding: 14,
    gap: 10,
    flexDirection: "row",
    alignItems: "center",
  },
  note: { flexDirection: "row", gap: 10, paddingHorizontal: 6 },
});
