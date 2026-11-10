import { QueryClient } from "@tanstack/react-query";
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, gcTime: 5 * 60_000, retry: 1 },
    mutations: { retry: false },
  },
});

const financeKeys = new Set([
  "home",
  "accounts",
  "categories",
  "transactions",
  "transaction",
  "history",
  "summary",
  "category-report",
  "evolution",
  "budgets",
  "goals",
  "installments",
  "planning-summary",
  "bills",
]);

export function invalidateFinance(client: QueryClient) {
  return client.invalidateQueries({
    predicate: (query) => financeKeys.has(String(query.queryKey[0])),
  });
}
