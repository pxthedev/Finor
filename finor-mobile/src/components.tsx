import {
  Pressable,
  Text,
  TextInput,
  View,
  ActivityIndicator,
  StyleSheet,
  TextInputProps,
} from "react-native";
import { theme } from "./theme";
export function Button({
  label,
  onPress,
  loading = false,
  variant = "primary",
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  loading?: boolean;
  variant?: "primary" | "secondary" | "ghost";
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={loading || disabled}
      accessibilityState={{ disabled: loading || disabled, busy: loading }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        variant === "secondary" && styles.secondaryButton,
        variant === "ghost" && styles.ghostButton,
        { opacity: disabled ? 0.4 : pressed ? 0.75 : 1 },
      ]}
    >
      {loading ? (
        <ActivityIndicator
          color={variant === "primary" ? "#07170E" : theme.colors.primary}
        />
      ) : (
        <Text
          style={[
            styles.buttonText,
            variant !== "primary" && { color: theme.colors.text },
          ]}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}
export function Input({ label, ...props }: TextInputProps & { label: string }) {
  return (
    <View style={{ gap: 8 }}>
      <Text
        style={{
          color: theme.colors.secondary,
          fontSize: 13,
          fontWeight: "500",
        }}
      >
        {label}
      </Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={theme.colors.tertiary}
        {...props}
        style={[styles.input, props.style]}
      />
    </View>
  );
}
export function EmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <View
      style={{
        padding: 24,
        gap: 8,
        backgroundColor: theme.colors.surface,
        borderRadius: 20,
        borderWidth: 1,
        borderColor: theme.colors.border,
      }}
    >
      <Text style={{ color: "#fff", fontSize: 17, fontWeight: "600" }}>
        {title}
      </Text>
      <Text style={{ color: theme.colors.secondary, lineHeight: 22 }}>
        {description}
      </Text>
    </View>
  );
}
export function LoadingState() {
  return (
    <ActivityIndicator
      accessibilityLabel="Carregando"
      color={theme.colors.primary}
    />
  );
}
export function ErrorState({ message }: { message: string }) {
  return (
    <Text
      accessibilityRole="alert"
      style={{
        color: theme.colors.expense,
        fontSize: 13,
        lineHeight: 20,
        padding: 12,
        backgroundColor: "#281719",
        borderRadius: 12,
      }}
    >
      {message}
    </Text>
  );
}
const styles = StyleSheet.create({
  button: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 15,
    minHeight: 52,
    borderRadius: 16,
    alignItems: "center",
  },
  secondaryButton: {
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  ghostButton: { backgroundColor: "transparent" },
  buttonText: { color: "#07170E", fontSize: 15, fontWeight: "700" },
  input: {
    backgroundColor: theme.colors.surface,
    color: "#fff",
    borderColor: theme.colors.border,
    borderWidth: 1,
    borderRadius: 16,
    padding: 16,
    fontSize: 16,
  },
});
