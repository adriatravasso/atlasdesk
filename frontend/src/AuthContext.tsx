import { useEffect, useMemo, useState } from "react";
import {
  ApiError,
  getCurrentUser,
  logout,
  onUnauthorized,
  resetSessionCache,
  type SessionUser,
} from "./api";
import { AuthContext, type AuthState } from "./auth-context";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setCurrentUser] = useState<SessionUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const unsubscribe = onUnauthorized(() => {
      resetSessionCache();
      if (active) {
        setCurrentUser(null);
        setError("Sua sessão expirou. Entre novamente para continuar.");
      }
    });
    getCurrentUser()
      .then((profile) => {
        if (active) setCurrentUser(profile);
      })
      .catch((cause: unknown) => {
        if (active && !(cause instanceof ApiError && cause.status === 401)) {
          setError("Não foi possível verificar sua sessão. Tente novamente.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      error,
      setUser: (profile) => {
        setError(null);
        setCurrentUser(profile);
      },
      signOut: async () => {
        await logout();
        setCurrentUser(null);
        setError(null);
      },
    }),
    [user, loading, error],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
