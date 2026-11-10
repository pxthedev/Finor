import { useQuery } from "@tanstack/react-query";
import { api, useSession } from "./api";
import { loadPlanningSummary } from "./planning-summary";
import { useScreenActive } from "./use-screen-active";

export function usePlanningSummary() {
  const uid = useSession((s) => s.session?.uid);
  const active = useScreenActive();
  return useQuery({
    queryKey: ["planning-summary", uid],
    queryFn: () => loadPlanningSummary(api),
    enabled: !!uid && active,
    retry: false,
  });
}
