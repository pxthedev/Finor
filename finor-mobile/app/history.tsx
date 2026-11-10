import { useMemo } from "react";
import { FlatList, Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { useInfiniteQuery } from "@tanstack/react-query";
import { api } from "../src/api";
import {
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
} from "../src/components";
import { TransactionItem } from "../src/finance-components";
import { useScreenActive } from "../src/use-screen-active";
type Entry = {
  id: string;
  description: string;
  category_name: string;
  type: string;
  amount: number;
  date: string;
};
export default function History() {
  const active = useScreenActive();
  const { categoryId, month } = useLocalSearchParams<{
    categoryId?: string;
    month?: string;
  }>();
  const history = useInfiniteQuery({
    enabled: active,
    queryKey: ["history", categoryId, month],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api<Entry[]>(
        "/transactions?page=" +
          pageParam +
          (categoryId ? "&categoryId=" + encodeURIComponent(categoryId) : "") +
          (month ? "&month=" + encodeURIComponent(month) : ""),
      ),
    getNextPageParam: (last, pages) =>
      last.length === 100 ? pages.length : undefined,
  });
  const entries = useMemo(
    () => history.data?.pages.flat() || [],
    [history.data],
  );
  return (
    <FlatList
      data={entries}
      keyExtractor={(item) => item.id}
      initialNumToRender={12}
      maxToRenderPerBatch={10}
      windowSize={7}
      contentContainerStyle={{ padding: 24, gap: 20 }}
      ListHeaderComponent={
        <View style={{ gap: 20 }}>
          <Text style={{ color: "#fff", fontSize: 24 }}>
            Histórico {month || ""}
          </Text>
          {history.isPending && <LoadingState />}
          {history.error && <ErrorState message={history.error.message} />}
        </View>
      }
      ListEmptyComponent={
        !history.isPending && !history.isError ? (
          <EmptyState
            title="Nenhum registro neste filtro"
            description="Ajuste o período ou registre uma transação."
          />
        ) : undefined
      }
      renderItem={({ item: t }) => (
        <TransactionItem
          key={t.id}
          name={t.description || "Transação"}
          category={t.category_name || "Sem categoria"}
          date={t.date}
          amount={Number(t.amount)}
          expense={t.type === "EXPENSE"}
          onPress={() =>
            router.push({ pathname: "/transaction", params: { editId: t.id } })
          }
        />
      )}
      ListFooterComponent={
        history.hasNextPage ? (
          <Button
            label="Carregar mais"
            loading={history.isFetchingNextPage}
            onPress={() => history.fetchNextPage()}
          />
        ) : undefined
      }
    />
  );
}
