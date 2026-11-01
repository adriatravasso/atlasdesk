import { useEffect, useRef } from "react";
import { backendReadyEvent } from "./backend-status-context";

export function useRefreshOnBackendReady(refresh: () => void) {
  const refreshRef = useRef(refresh);
  useEffect(() => {
    refreshRef.current = refresh;
  }, [refresh]);
  useEffect(() => {
    const onReady = () => refreshRef.current();
    window.addEventListener(backendReadyEvent, onReady);
    return () => window.removeEventListener(backendReadyEvent, onReady);
  }, []);
}
