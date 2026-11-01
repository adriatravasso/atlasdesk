import { useCallback, useEffect, useState } from "react";
import {
  BackendStatusContext,
  backendReadyEvent,
  backendUnavailableEvent,
  type BackendStatus,
} from "./backend-status-context";
const PROBE_TIMEOUT_MS = 6_000;
const PROBE_INTERVAL_MS = 8_000;
const OFFLINE_INTERVAL_MS = 30_000;
const OFFLINE_AFTER_FAILURES = 10;

export function BackendStatusProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [retryToken, setRetryToken] = useState(0);
  const [status, setStatus] = useState<BackendStatus>("checking");

  const retry = useCallback(() => {
    setStatus("starting");
    setRetryToken((token) => token + 1);
  }, []);

  useEffect(() => {
    let active = true;
    let timer: number | undefined;
    let failures = 0;
    let wasReady = false;
    let wasUnavailable = false;

    async function probe() {
      const controller = new AbortController();
      const timeout = window.setTimeout(
        () => controller.abort(),
        PROBE_TIMEOUT_MS,
      );
      try {
        const response = await fetch("/api/health", {
          credentials: "same-origin",
          signal: controller.signal,
        });
        if (!response.ok) throw new Error("Backend health check failed");
        failures = 0;
        if (!active) return;
        setStatus("ready");
        if (!wasReady && wasUnavailable) {
          window.dispatchEvent(new Event(backendReadyEvent));
        }
        wasReady = true;
        wasUnavailable = false;
      } catch {
        if (!active) return;
        failures += 1;
        wasUnavailable = true;
        setStatus(failures >= OFFLINE_AFTER_FAILURES ? "offline" : "starting");
        timer = window.setTimeout(
          probe,
          failures >= OFFLINE_AFTER_FAILURES
            ? OFFLINE_INTERVAL_MS
            : PROBE_INTERVAL_MS,
        );
      } finally {
        window.clearTimeout(timeout);
      }
    }

    function onUnavailable() {
      if (!active || !wasReady) return;
      wasReady = false;
      wasUnavailable = true;
      failures = 0;
      if (timer !== undefined) window.clearTimeout(timer);
      setStatus("starting");
      void probe();
    }

    window.addEventListener(backendUnavailableEvent, onUnavailable);
    void probe();
    return () => {
      active = false;
      window.removeEventListener(backendUnavailableEvent, onUnavailable);
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [retryToken]);

  return (
    <BackendStatusContext.Provider value={{ status, retry }}>
      {children}
    </BackendStatusContext.Provider>
  );
}
