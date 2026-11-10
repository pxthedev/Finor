import { memo, useMemo } from "react";
import { Text, View } from "react-native";
import Svg, { Circle, Path } from "react-native-svg";
import { formatMoney, theme } from "./theme";

export type ExpenseCategory = {
  id: string;
  name: string;
  spent: number;
};

const colors = [
  "#19C96A",
  "#56B8EF",
  "#F2B84B",
  "#AC8AFF",
  "#F36B6B",
  "#48D5C4",
  "#F19AC1",
  "#90B969",
];

export const ExpensePie = memo(function ExpensePie({
  categories,
}: {
  categories: ExpenseCategory[];
}) {
  const slices = useMemo(
    () =>
      categories
        .filter((c) => Number(c.spent) > 0)
        .map(
          (c) =>
            [
              c.id || "uncategorized",
              { name: c.name, cents: Math.round(Number(c.spent) * 100) },
            ] as const,
        ),
    [categories],
  );
  const total = slices.reduce((sum, [, group]) => sum + group.cents, 0);
  let angle = -Math.PI / 2;

  return (
    <View
      style={{
        backgroundColor: theme.colors.surface,
        borderRadius: 24,
        padding: 20,
        gap: 20,
        borderWidth: 1,
        borderColor: theme.colors.border,
      }}
    >
      <View style={{ gap: 6 }}>
        <Text
          style={{ color: theme.colors.text, fontSize: 20, fontWeight: "700" }}
        >
          Gastos por categoria
        </Text>
        <Text style={{ color: theme.colors.secondary }}>
          Todo o histórico registrado
        </Text>
        <Text
          style={{ color: theme.colors.text, fontSize: 30, fontWeight: "700" }}
        >
          {formatMoney(total / 100)}
        </Text>
      </View>
      {total === 0 ? (
        <Text style={{ color: theme.colors.secondary, lineHeight: 22 }}>
          Seus gastos aparecerão no gráfico assim que você registrar a primeira
          despesa.
        </Text>
      ) : (
        <>
          <View
            accessible
            accessibilityLabel={`Gráfico de pizza dos gastos por categoria. Total ${formatMoney(total / 100)}.`}
            style={{ alignItems: "center" }}
          >
            <Svg width={240} height={240} viewBox="0 0 240 240">
              {slices.map(([id, group], index) => {
                const start = angle;
                const sweep = (group.cents / total) * Math.PI * 2;
                angle += sweep;
                const fill = colors[index % colors.length];
                if (slices.length === 1)
                  return (
                    <Circle key={id} cx={120} cy={120} r={112} fill={fill} />
                  );
                const x1 = 120 + 112 * Math.cos(start);
                const y1 = 120 + 112 * Math.sin(start);
                const x2 = 120 + 112 * Math.cos(angle);
                const y2 = 120 + 112 * Math.sin(angle);
                return (
                  <Path
                    key={id}
                    d={`M120,120 L${x1},${y1} A112,112 0 ${sweep > Math.PI ? 1 : 0},1 ${x2},${y2} Z`}
                    fill={fill}
                    stroke={theme.colors.surface}
                    strokeWidth={2}
                  />
                );
              })}
            </Svg>
          </View>
          <View style={{ gap: 16 }}>
            {slices.map(([id, group], index) => (
              <View
                key={id}
                style={{ flexDirection: "row", alignItems: "center", gap: 10 }}
              >
                <View
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: 5,
                    backgroundColor: colors[index % colors.length],
                  }}
                />
                <Text style={{ flex: 1, color: theme.colors.text }}>
                  {group.name}
                </Text>
                <View style={{ alignItems: "flex-end", gap: 3 }}>
                  <Text style={{ color: theme.colors.text, fontWeight: "600" }}>
                    {formatMoney(group.cents / 100)}
                  </Text>
                  <Text style={{ color: theme.colors.secondary }}>
                    {((group.cents / total) * 100).toLocaleString("pt-BR", {
                      maximumFractionDigits: 1,
                    })}
                    %
                  </Text>
                </View>
              </View>
            ))}
          </View>
        </>
      )}
    </View>
  );
});
