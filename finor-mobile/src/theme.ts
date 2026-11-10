export const theme = {
  colors: {
    background: "#070B0A",
    surface: "#101815",
    elevated: "#18231E",
    border: "#263A30",
    primarySoft: "#102E20",
    primary: "#19C96A",
    primaryDark: "#087D43",
    text: "#FFFFFF",
    secondary: "#A8B9AF",
    tertiary: "#7E9588",
    expense: "#F36B6B",
    warning: "#F2B84B",
  },
  spacing: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
  typography: {
    display: 32,
    h1: 24,
    h2: 20,
    body: 16,
    secondary: 14,
    caption: 12,
  },
};
const moneyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});
export const formatMoney = (value: number) => moneyFormatter.format(value);
