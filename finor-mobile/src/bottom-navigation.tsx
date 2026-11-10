import { memo } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { router } from "expo-router";
import {
  House,
  ChartNoAxesColumn,
  MessageCircle,
  Target,
  Menu,
} from "lucide-react-native";
import { theme } from "./theme";

const tabs = [
  {
    path: "/home",
    label: "Início",
    Icon: House,
    routes: ["/home", "/income", "/simulation", "/recurring"],
  },
  {
    path: "/reports",
    label: "Gastos",
    Icon: ChartNoAxesColumn,
    routes: ["/reports", "/history", "/transaction", "/budgets"],
  },
  {
    path: "/assistant",
    label: "Chat",
    Icon: MessageCircle,
    routes: ["/assistant"],
  },
  {
    path: "/goals",
    label: "Metas",
    Icon: Target,
    routes: ["/goals", "/planning"],
  },
  {
    path: "/more",
    label: "Mais",
    Icon: Menu,
    routes: ["/more", "/profile", "/privacy"],
  },
] as const;

export const BottomNavigation = memo(function BottomNavigation({
  path,
}: {
  path: string;
}) {
  return (
    <SafeAreaView edges={["bottom"]} style={styles.safeArea}>
      <View style={styles.dock} accessibilityRole="tablist">
        {tabs.map(({ path: route, label, Icon, routes }) => {
          const selected = (routes as readonly string[]).includes(path);
          const chat = route === "/assistant";
          return (
            <Pressable
              key={route}
              accessibilityRole="tab"
              accessibilityLabel={label}
              accessibilityState={{ selected }}
              onPress={() => {
                if (path !== route) router.navigate(route);
              }}
              style={({ pressed }) => [
                styles.tab,
                selected && !chat && styles.selectedTab,
                {
                  opacity: pressed ? 0.7 : 1,
                  transform: [{ scale: pressed ? 0.96 : 1 }],
                },
              ]}
            >
              <View
                style={[
                  styles.icon,
                  chat && styles.chatIcon,
                  chat && selected && styles.chatSelected,
                ]}
              >
                <Icon
                  size={chat ? 23 : 22}
                  strokeWidth={selected ? 2.3 : 1.8}
                  color={
                    chat
                      ? "#052B17"
                      : selected
                        ? theme.colors.primary
                        : theme.colors.tertiary
                  }
                />
                {chat && <View style={styles.chatSpark} />}
              </View>
              <Text
                numberOfLines={1}
                style={[styles.label, selected && styles.selectedLabel]}
              >
                {label}
              </Text>
              <View
                style={[styles.indicator, selected && styles.activeIndicator]}
              />
            </Pressable>
          );
        })}
      </View>
    </SafeAreaView>
  );
});

const styles = StyleSheet.create({
  safeArea: {
    backgroundColor: theme.colors.background,
    paddingHorizontal: 12,
    paddingTop: 8,
    paddingBottom: 6,
  },
  dock: {
    flexDirection: "row",
    alignItems: "center",
    padding: 5,
    gap: 3,
    borderRadius: 28,
    backgroundColor: "#111E18",
    borderWidth: 1,
    borderColor: "#294234",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.22,
    shadowRadius: 12,
    elevation: 6,
  },
  tab: {
    flex: 1,
    minHeight: 64,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 22,
    gap: 3,
    paddingVertical: 5,
  },
  selectedTab: { backgroundColor: "#1A3525" },
  icon: {
    height: 28,
    width: 40,
    alignItems: "center",
    justifyContent: "center",
  },
  chatIcon: {
    backgroundColor: theme.colors.primary,
    borderRadius: 15,
    width: 43,
    height: 34,
    shadowColor: theme.colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
  },
  chatSelected: { backgroundColor: "#4CE58D" },
  chatSpark: {
    position: "absolute",
    right: 5,
    top: 5,
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: "#C3FFDA",
  },
  label: { color: theme.colors.tertiary, fontSize: 10, fontWeight: "500" },
  selectedLabel: { color: "#A8F5C4", fontWeight: "700" },
  indicator: {
    width: 12,
    height: 2,
    borderRadius: 2,
    backgroundColor: "transparent",
  },
  activeIndicator: { backgroundColor: theme.colors.primary },
});
