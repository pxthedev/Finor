import { ReactNode } from "react";
import {
  Modal as NativeModal,
  View,
  Text,
  Pressable,
  StyleSheet,
} from "react-native";
import { Button, Input } from "./components";
import { theme, formatMoney } from "./theme";
export function CurrencyInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <Input
      label={label}
      keyboardType="decimal-pad"
      value={value.replace(".", ",")}
      onChangeText={(text) => {
        if (/^\d{0,17}([.,]\d{0,2})?$/.test(text))
          onChange(text.replace(",", "."));
      }}
    />
  );
}
export function FinanceCard({ children }: { children: ReactNode }) {
  return <View style={styles.card}>{children}</View>;
}
export function ProgressBar({
  value,
  total,
}: {
  value: number;
  total: number;
}) {
  const percent = Math.min(
    100,
    Math.max(0, total > 0 ? (value / total) * 100 : 0),
  );
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: percent }}
      style={{ height: 4, backgroundColor: theme.colors.border }}
    >
      <View
        style={{
          height: 4,
          width: `${percent}%`,
          backgroundColor:
            value > total ? theme.colors.warning : theme.colors.primary,
        }}
      />
    </View>
  );
}
export function CategoryItem({
  name,
  spent,
  limit,
}: {
  name: string;
  spent: number;
  limit: number;
}) {
  return (
    <View style={{ gap: 8 }}>
      <Text style={styles.text}>{name}</Text>
      <Text style={styles.secondary}>
        {formatMoney(spent)} / {formatMoney(limit)}
      </Text>
      <ProgressBar value={spent} total={limit} />
    </View>
  );
}
export function TransactionItem({
  name,
  category,
  date,
  amount,
  expense,
  onPress,
}: {
  name: string;
  category: string;
  date: string;
  amount: number;
  expense: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={{
        paddingVertical: 16,
        borderBottomWidth: 1,
        borderColor: theme.colors.border,
        gap: 6,
      }}
    >
      <Text style={styles.text}>{name}</Text>
      <Text style={styles.secondary}>
        {category} · {date}
      </Text>
      <Text
        style={{
          color: expense ? theme.colors.expense : theme.colors.primary,
          fontVariant: ["tabular-nums"],
        }}
      >
        {formatMoney(amount)}
      </Text>
    </Pressable>
  );
}
export function Modal({
  visible,
  onClose,
  children,
}: {
  visible: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <NativeModal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <View accessibilityViewIsModal style={styles.card}>
          {children}
          <Button label="Fechar" onPress={onClose} />
        </View>
      </View>
    </NativeModal>
  );
}
export function BottomSheet({
  visible,
  onClose,
  children,
}: {
  visible: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <NativeModal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View
        style={[styles.overlay, { justifyContent: "flex-end", padding: 0 }]}
      >
        <View
          accessibilityViewIsModal
          style={[
            styles.card,
            { borderBottomLeftRadius: 0, borderBottomRightRadius: 0 },
          ]}
        >
          {children}
          <Button label="Fechar" onPress={onClose} />
        </View>
      </View>
    </NativeModal>
  );
}
export function ChatBubble({
  message,
  mine = false,
}: {
  message: string;
  mine?: boolean;
}) {
  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: mine ? theme.colors.elevated : theme.colors.surface,
        },
      ]}
    >
      <Text style={styles.text}>{message}</Text>
    </View>
  );
}
export function ConfirmationCard({
  children,
  onConfirm,
  onEdit,
  onCancel,
  busy = false,
}: {
  children: ReactNode;
  onConfirm: () => void;
  onEdit: () => void;
  onCancel: () => void;
  busy?: boolean;
}) {
  return (
    <FinanceCard>
      {children}
      <Button label="Confirmar" loading={busy} onPress={onConfirm} />
      <Button label="Editar" loading={busy} onPress={onEdit} />
      <Button label="Cancelar" loading={busy} onPress={onCancel} />
    </FinanceCard>
  );
}
export function Toast({ message }: { message: string }) {
  return (
    <View accessibilityLiveRegion="polite" style={styles.card}>
      <Text style={styles.text}>{message}</Text>
    </View>
  );
}
const styles = StyleSheet.create({
  card: {
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.border,
    borderWidth: 1,
    borderRadius: 16,
    padding: 20,
    gap: 16,
  },
  text: { color: theme.colors.text, fontSize: 16 },
  secondary: { color: theme.colors.secondary, fontSize: 14 },
  overlay: {
    flex: 1,
    justifyContent: "center",
    padding: 24,
    backgroundColor: "rgba(0,0,0,0.8)",
  },
});
