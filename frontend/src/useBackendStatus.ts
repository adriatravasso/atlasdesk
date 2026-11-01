import { useContext } from "react";
import { BackendStatusContext } from "./backend-status-context";

export function useBackendStatus() {
  const status = useContext(BackendStatusContext);
  if (!status) {
    throw new Error(
      "useBackendStatus must be used within BackendStatusProvider",
    );
  }
  return status;
}
