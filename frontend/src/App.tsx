import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  ApiError,
  login,
  request,
  type SessionUser,
  type UserPage,
  type UserRole,
} from "./api";
import { AuthProvider } from "./AuthContext";
import { useAuth } from "./useAuth";

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedLayout />}>
            <Route index element={<OverviewPage />} />
            <Route
              path="users"
              element={
                <RoleRoute roles={["ADMIN"]}>
                  <UsersPage />
                </RoleRoute>
              }
            />
            <Route path="forbidden" element={<ForbiddenPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

function LoadingPage() {
  return (
    <main className="status-page" role="status">
      Verificando sua sessão…
    </main>
  );
}

function ProtectedLayout() {
  const { user, loading, error } = useAuth();
  const location = useLocation();
  if (loading) return <LoadingPage />;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  return (
    <Shell user={user} error={error}>
      <Outlet />
    </Shell>
  );
}

function RoleRoute({
  roles,
  children,
}: {
  roles: UserRole[];
  children: React.ReactNode;
}) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role))
    return <Navigate to="/forbidden" replace />;
  return <>{children}</>;
}

function Shell({
  user,
  error,
  children,
}: {
  user: SessionUser;
  error: string | null;
  children: React.ReactNode;
}) {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const [logoutError, setLogoutError] = useState<string | null>(null);
  async function handleLogout() {
    setLogoutError(null);
    try {
      await signOut();
      navigate("/login", { replace: true });
    } catch {
      setLogoutError("Não foi possível encerrar a sessão. Tente novamente.");
    }
  }
  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Navegação principal">
        <Link className="brand" to="/" aria-label="AtlasDesk, início">
          <span className="brand-mark">A</span>
          <span>AtlasDesk</span>
        </Link>
        <p className="nav-label">ESPAÇO DE TRABALHO</p>
        <nav className="side-links">
          <NavLink to="/" end>
            Visão geral
          </NavLink>
          {user.role === "ADMIN" && <NavLink to="/users">Usuários</NavLink>}
        </nav>
        <div className="sidebar-foot">
          <span className="workspace-dot" />{" "}
          {user.organizationId
            ? `Organização ${user.organizationId}`
            : "Espaço de trabalho"}
        </div>
      </aside>
      <div className="main-column">
        <header className="app-header">
          <div className="mobile-brand">
            <span className="brand-mark">A</span> AtlasDesk
          </div>
          <div className="header-spacer" />
          <span className="session-pill">
            <span className="session-dot" /> Sessão ativa
          </span>
          <div className="user-menu">
            <span className="avatar" aria-hidden="true">
              {user.name.slice(0, 1).toUpperCase()}
            </span>
            <span className="user-copy">
              <strong>{user.name}</strong>
              <small>{user.role}</small>
            </span>
          </div>
          <button className="button button--quiet" onClick={handleLogout}>
            Sair
          </button>
        </header>
        {error && (
          <div className="notice notice--warning" role="status">
            {error}
          </div>
        )}
        {logoutError && (
          <div className="notice notice--error" role="alert">
            {logoutError}
          </div>
        )}
        <main className="content">{children}</main>
        <nav className="mobile-nav" aria-label="Navegação móvel">
          <NavLink to="/" end>
            Início
          </NavLink>
          {user.role === "ADMIN" && <NavLink to="/users">Usuários</NavLink>}
        </nav>
      </div>
    </div>
  );
}

function OverviewPage() {
  const { user } = useAuth();
  return (
    <section className="page-content">
      <p className="eyebrow">VISÃO GERAL</p>
      <h1>Olá, {user?.name.split(" ")[0]}.</h1>
      <p className="page-lead">Este é o seu espaço de trabalho no AtlasDesk.</p>
      <div className="welcome-card">
        <div className="card-icon" aria-hidden="true">
          ✓
        </div>
        <div>
          <h2>Tudo pronto para começar</h2>
          <p>
            As funcionalidades disponíveis aparecerão aqui quando estiverem
            habilitadas para sua equipe.
          </p>
        </div>
      </div>
      <p className="quiet-note">
        Sua sessão é protegida e será encerrada quando você sair ou quando
        expirar.
      </p>
    </section>
  );
}

function ForbiddenPage() {
  return (
    <section className="page-content">
      <p className="eyebrow">ACESSO RESTRITO</p>
      <h1>Acesso não autorizado</h1>
      <p className="page-lead">
        Seu perfil não tem permissão para abrir esta página.
      </p>
      <Link className="button button--primary" to="/">
        Voltar à visão geral
      </Link>
    </section>
  );
}

function LoginPage() {
  const { user, loading, setUser, error: sessionError } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [organizationId, setOrganizationId] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const checkedInitialSession = useRef(false);
  const from =
    (location.state as { from?: { pathname?: string } } | null)?.from
      ?.pathname ?? "/";
  useEffect(() => {
    if (loading || checkedInitialSession.current) return;
    checkedInitialSession.current = true;
    if (user) navigate("/", { replace: true });
  }, [loading, user, navigate]);
  if (loading) return <LoadingPage />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const profile = await login({
        organizationId: Number(organizationId),
        email,
        password,
      });
      setUser(profile);
      navigate(from === "/login" ? "/" : from, { replace: true });
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.status === 401
          ? "Organização, e-mail ou senha inválidos."
          : "Não foi possível entrar. Verifique os dados e tente novamente.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <Link className="brand login-brand" to="/" aria-label="AtlasDesk">
          <span className="brand-mark">A</span>
          <span>AtlasDesk</span>
        </Link>
        <p className="eyebrow">ACESSO SEGURO</p>
        <h1 id="login-title">Entre no seu espaço.</h1>
        <p className="page-lead">
          Use suas credenciais AtlasDesk para continuar.
        </p>
        {sessionError && (
          <div className="notice notice--warning" role="status">
            {sessionError}
          </div>
        )}
        {error && (
          <div className="notice notice--error" role="alert">
            {error}
          </div>
        )}
        <form className="login-form" onSubmit={submit}>
          <label htmlFor="organizationId">ID da organização</label>
          <input
            id="organizationId"
            name="organizationId"
            inputMode="numeric"
            pattern="[0-9]+"
            autoComplete="organization"
            value={organizationId}
            onChange={(event) => setOrganizationId(event.target.value)}
            required
          />
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
          <label htmlFor="password">Senha</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          <button
            className="button button--primary button--wide"
            type="submit"
            disabled={busy}
          >
            {busy ? "Entrando…" : "Entrar"}
          </button>
        </form>
        <p className="login-footnote">
          O acesso é controlado pela sua organização.
        </p>
      </section>
    </main>
  );
}

function UsersPage() {
  const [page, setPage] = useState<UserPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    request<UserPage>("/users?page=0&size=20")
      .then((result) => {
        if (active) setPage(result);
      })
      .catch((cause: unknown) => {
        if (active)
          setError(
            cause instanceof ApiError && cause.status === 403
              ? "Sua conta não tem acesso à administração de usuários."
              : "Não foi possível carregar os usuários.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);
  return (
    <section className="page-content">
      <p className="eyebrow">ADMINISTRAÇÃO</p>
      <h1>Usuários</h1>
      <p className="page-lead">Pessoas cadastradas na sua organização.</p>
      {loading && <p role="status">Carregando usuários…</p>}
      {error && (
        <div className="notice notice--error" role="alert">
          {error}
        </div>
      )}
      {page && (
        <div className="table-card">
          <div className="table-heading">
            <h2>Diretório</h2>
            <span>
              {page.totalElements}{" "}
              {page.totalElements === 1 ? "pessoa" : "pessoas"}
            </span>
          </div>
          {page.content.length === 0 ? (
            <p className="empty-state">
              Ainda não há usuários cadastrados nesta organização.
            </p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Nome</th>
                    <th>E-mail</th>
                    <th>Perfil</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {page.content.map((person) => (
                    <tr key={person.id}>
                      <td>{person.name}</td>
                      <td>{person.email}</td>
                      <td>{roleName(person.role)}</td>
                      <td>
                        <span
                          className={`status-tag ${person.active ? "status-tag--active" : ""}`}
                        >
                          {person.active ? "Ativo" : "Inativo"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function roleName(role: UserRole) {
  return { ADMIN: "Administrador", AGENT: "Agente", REQUESTER: "Solicitante" }[
    role
  ];
}

export default App;
