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
  const [pageNumber, setPageNumber] = useState(0);
  const [reloadToken, setReloadToken] = useState(0);
  const [listError, setListError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyUserId, setBusyUserId] = useState<number | null>(null);
  const [form, setForm] = useState({
    name: "",
    email: "",
    role: "REQUESTER" as UserRole,
    active: true,
    password: "",
  });
  useEffect(() => {
    let active = true;
    setLoading(true);
    setListError(null);
    request<UserPage>(`/users?page=${pageNumber}&size=20`)
      .then((result) => {
        if (active) setPage(result);
      })
      .catch((cause: unknown) => {
        if (active) {
          setListError(
            cause instanceof ApiError
              ? cause.status === 403
                ? "Sua conta não tem acesso à administração de usuários."
                : cause.detail
              : "Não foi possível carregar os usuários. Tente novamente.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [pageNumber, reloadToken]);

  function startCreate() {
    setEditingId(null);
    setForm({
      name: "",
      email: "",
      role: "REQUESTER",
      active: true,
      password: "",
    });
    setFormError(null);
    setNotice(null);
    setFormOpen(true);
  }

  function startEdit(person: UserPage["content"][number]) {
    setEditingId(person.id);
    setForm({
      name: person.name,
      email: person.email,
      role: person.role,
      active: person.active,
      password: "",
    });
    setFormError(null);
    setNotice(null);
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setFormError(null);
  }

  async function saveUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!form.name.trim() || form.name.trim().length > 160) {
      setFormError("Informe um nome com até 160 caracteres.");
      return;
    }
    if (!form.email.trim() || form.email.trim().length > 320) {
      setFormError("Informe um e-mail válido com até 320 caracteres.");
      return;
    }
    if (
      (!editingId && form.password.length < 12) ||
      form.password.length > 72
    ) {
      setFormError(
        editingId
          ? "A nova senha deve ter entre 12 e 72 caracteres."
          : "A senha deve ter entre 12 e 72 caracteres.",
      );
      return;
    }
    setSaving(true);
    try {
      const input = {
        name: form.name.trim(),
        email: form.email.trim(),
        role: form.role,
        active: form.active,
        ...(form.password ? { password: form.password } : {}),
      };
      if (editingId) {
        await request(`/users/${editingId}`, {
          method: "PATCH",
          body: JSON.stringify(input),
        });
        setNotice("As alterações do usuário foram salvas.");
      } else {
        await request("/users", {
          method: "POST",
          body: JSON.stringify(input),
        });
        setPageNumber(0);
        setNotice("O usuário foi cadastrado.");
      }
      setFormOpen(false);
      setEditingId(null);
      setReloadToken((token) => token + 1);
    } catch (cause) {
      setFormError(
        cause instanceof ApiError
          ? cause.detail
          : "Não foi possível salvar o usuário. Tente novamente.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function toggleUser(person: UserPage["content"][number]) {
    if (
      person.active &&
      !window.confirm(`Deseja desativar a conta de ${person.name}?`)
    ) {
      return;
    }
    setNotice(null);
    setBusyUserId(person.id);
    try {
      await request(`/users/${person.id}`, {
        method: "PATCH",
        body: JSON.stringify({ active: !person.active }),
      });
      setNotice(
        person.active
          ? `A conta de ${person.name} foi desativada.`
          : `A conta de ${person.name} foi reativada.`,
      );
      setReloadToken((token) => token + 1);
    } catch (cause) {
      setNotice(
        cause instanceof ApiError
          ? cause.detail
          : "Não foi possível atualizar o estado do usuário.",
      );
    } finally {
      setBusyUserId(null);
    }
  }

  const totalPages = page?.totalPages ?? 0;
  const visiblePage = totalPages === 0 ? 0 : pageNumber + 1;
  return (
    <section className="page-content">
      <div className="users-title-row">
        <div>
          <p className="eyebrow">ADMINISTRAÇÃO</p>
          <h1>Usuários</h1>
          <p className="page-lead">Gerencie as contas da sua organização.</p>
        </div>
        <button className="button button--primary" onClick={startCreate}>
          Novo usuário
        </button>
      </div>
      {notice && (
        <div className="notice notice--success" role="status">
          {notice}
        </div>
      )}
      {formOpen && (
        <form className="user-form-card" onSubmit={saveUser}>
          <div className="user-form-heading">
            <div>
              <p className="eyebrow">CONTA</p>
              <h2>{editingId ? "Editar usuário" : "Cadastrar usuário"}</h2>
            </div>
            <button
              className="button button--quiet"
              type="button"
              onClick={closeForm}
              aria-label="Fechar formulário"
            >
              Fechar
            </button>
          </div>
          {formError && (
            <div className="notice notice--error" role="alert">
              {formError}
            </div>
          )}
          <div className="user-form-grid">
            <label>
              Nome
              <input
                name="name"
                value={form.name}
                maxLength={160}
                onChange={(event) =>
                  setForm({ ...form, name: event.target.value })
                }
                required
              />
            </label>
            <label>
              E-mail
              <input
                name="email"
                type="email"
                value={form.email}
                maxLength={320}
                onChange={(event) =>
                  setForm({ ...form, email: event.target.value })
                }
                required
              />
            </label>
            <label>
              Perfil
              <select
                name="role"
                value={form.role}
                onChange={(event) =>
                  setForm({ ...form, role: event.target.value as UserRole })
                }
              >
                <option value="ADMIN">Administrador</option>
                <option value="AGENT">Agente</option>
                <option value="REQUESTER">Solicitante</option>
              </select>
            </label>
            <label className="user-password-field">
              Senha {editingId ? "(opcional)" : "(mínimo 12 caracteres)"}
              <input
                name="password"
                type="password"
                value={form.password}
                minLength={editingId ? undefined : 12}
                maxLength={72}
                autoComplete="new-password"
                onChange={(event) =>
                  setForm({ ...form, password: event.target.value })
                }
                required={!editingId}
              />
            </label>
            <label className="user-active-field">
              <input
                name="active"
                type="checkbox"
                checked={form.active}
                onChange={(event) =>
                  setForm({ ...form, active: event.target.checked })
                }
              />
              Conta ativa
            </label>
          </div>
          <div className="user-form-actions">
            <button
              className="button button--quiet"
              type="button"
              onClick={closeForm}
              disabled={saving}
            >
              Cancelar
            </button>
            <button
              className="button button--primary"
              type="submit"
              disabled={saving}
            >
              {saving
                ? "Salvando…"
                : editingId
                  ? "Salvar alterações"
                  : "Cadastrar"}
            </button>
          </div>
        </form>
      )}
      {loading && <p role="status">Carregando usuários…</p>}
      {listError && (
        <div className="notice notice--error" role="alert">
          <span>{listError}</span>{" "}
          <button
            className="text-button"
            onClick={() => setReloadToken((token) => token + 1)}
          >
            Tentar novamente
          </button>
        </div>
      )}
      {page && (
        <div className="table-card">
          <div className="table-heading">
            <h2>Diretório</h2>
            <span>
              {page.totalElements}{" "}
              {page.totalElements === 1 ? "conta" : "contas"}
            </span>
          </div>
          {page.content.length === 0 ? (
            <p className="empty-state">
              Ainda não há usuários nesta organização.
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
                    <th>Ações</th>
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
                      <td className="user-row-actions">
                        <div>
                          <button
                            className="text-button"
                            onClick={() => startEdit(person)}
                          >
                            Editar
                          </button>
                          <button
                            className={`text-button ${person.active ? "text-button--danger" : ""}`}
                            onClick={() => void toggleUser(person)}
                            disabled={busyUserId === person.id}
                          >
                            {busyUserId === person.id
                              ? "Salvando…"
                              : person.active
                                ? "Desativar"
                                : "Reativar"}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="pagination" aria-label="Paginação de usuários">
            <span>
              Página {visiblePage} de {Math.max(totalPages, 1)}
            </span>
            <div>
              <button
                className="button button--quiet"
                onClick={() => setPageNumber((value) => Math.max(0, value - 1))}
                disabled={loading || pageNumber === 0 || totalPages === 0}
              >
                Anterior
              </button>
              <button
                className="button button--quiet"
                onClick={() => setPageNumber((value) => value + 1)}
                disabled={loading || pageNumber + 1 >= totalPages}
              >
                Próxima
              </button>
            </div>
          </div>
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
