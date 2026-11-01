import { createContext } from "react";

export type BackendStatus = "checking" | "starting" | "ready" | "offline";

type BackendStatusValue = {
  status: BackendStatus;
  retry: () => void;
};

export const BackendStatusContext = createContext<BackendStatusValue | null>(
  null,
);

export const backendUnavailableEvent = "atlasdesk:backend-unavailable";
export const backendReadyEvent = "atlasdesk:backend-ready";
