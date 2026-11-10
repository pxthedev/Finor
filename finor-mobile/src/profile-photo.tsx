import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Image, Text, View } from "react-native";
import { api, useSession } from "./api";
import { theme } from "./theme";
import { useScreenActive } from "./use-screen-active";

export type ProfilePhoto = { dataUrl: string | null };
export const profilePhotoKey = (uid?: string) =>
  ["profile-photo", uid] as const;
export function useProfilePhoto() {
  const uid = useSession((s) => s.session?.uid);
  const active = useScreenActive();
  return useQuery({
    queryKey: profilePhotoKey(uid),
    queryFn: () => api<ProfilePhoto>("/users/me/photo"),
    enabled: !!uid && active,
    staleTime: 5 * 60_000,
  });
}

export function ProfileAvatar({
  name,
  photo,
  size = 48,
}: {
  name?: string;
  photo?: string | null;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [photo]);
  const words = (name?.trim() || "Finor").split(/\s+/);
  const initials = (
    words[0][0] + (words.length > 1 ? words[words.length - 1][0] : "")
  ).toUpperCase();
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        overflow: "hidden",
        backgroundColor: theme.colors.primarySoft,
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      {photo && !failed ? (
        <Image
          accessibilityLabel="Sua foto de perfil"
          source={{ uri: photo }}
          onError={() => setFailed(true)}
          style={{ width: size, height: size }}
          resizeMode="cover"
        />
      ) : (
        <Text
          style={{
            color: theme.colors.primary,
            fontSize: size * 0.34,
            fontWeight: "700",
          }}
        >
          {initials}
        </Text>
      )}
    </View>
  );
}
