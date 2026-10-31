import { createContext } from "react";
import type { SessionUser } from "./api";

export type AuthState = {
  user: SessionUser | null;
  loading: boolean;
  error: string | null;
  setUser: (user: SessionUser) => void;
  signOut: () => Promise<void>;
};

export const AuthContext = createContext<AuthState | null>(null);
