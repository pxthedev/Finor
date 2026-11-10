import { useCallback, useState } from "react";
import { useFocusEffect } from "expo-router";

/** Hidden stack screens keep their cache without starting background requests. */
export function useScreenActive() {
  const [active, setActive] = useState(false);
  useFocusEffect(
    useCallback(() => {
      setActive(true);
      return () => setActive(false);
    }, []),
  );
  return active;
}
