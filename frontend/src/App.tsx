import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Outlet,
  Route,
  Routes,
  useParams,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import {
  ApiError,
  type Asset,
  type AssetPage,
  type AssetStatus,
  type Ticket,
  type TicketPage,
  type TicketPriority,
  type TicketStatus,
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
            <Route path="tickets" element={<TicketsPage />} />
            <Route path="tickets/new" element={<TicketCreatePage />} />
            <Route path="tickets/:ticketId" element={<TicketDetailPage />} />
            <Route
              path="assets"
              element={
                <RoleRoute roles={["ADMIN", "AGENT", "REQUESTER"]}>
                  <AssetsPage />
                </RoleRoute>
              }
            />
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
          <NavLink to="/tickets">Chamados</NavLink>
          <NavLink to="/assets">Ativos</NavLink>
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
          <NavLink to="/tickets">Chamados</NavLink>
          <NavLink to="/assets">Ativos</NavLink>
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

function TicketsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedPage = Number.parseInt(searchParams.get("page") ?? "0", 10);
  const pageNumber = Number.isFinite(requestedPage)
    ? Math.max(0, requestedPage)
    : 0;
  const filters = {
    search: searchParams.get("search") ?? "",
    status: searchParams.get("status") ?? "",
    priority: searchParams.get("priority") ?? "",
  };
  const [searchInput, setSearchInput] = useState(filters.search);
  const [page, setPage] = useState<TicketPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const queryKey = searchParams.toString();

  useEffect(() => {
    setSearchInput(filters.search);
  }, [filters.search]);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams(queryKey);
    const parsedPage = Number.parseInt(params.get("page") ?? "0", 10);
    const query = new URLSearchParams({
      page: String(Number.isFinite(parsedPage) ? Math.max(0, parsedPage) : 0),
      size: "20",
    });
    for (const name of ["search", "status", "priority"])
      if (params.has(name)) query.set(name, params.get(name) ?? "");
    setLoading(true);
    setError(null);
    request<TicketPage>(`/tickets?${query.toString()}`)
      .then((result) => {
        if (active) setPage(result);
      })
      .catch((cause: unknown) => {
        if (active)
          setError(
            cause instanceof ApiError
              ? cause.detail
              : "Não foi possível carregar os chamados. Tente novamente.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [queryKey, reloadToken]);

  function updateFilters(next: {
    search?: string;
    status?: string;
    priority?: string;
    page?: string;
  }) {
    const params = new URLSearchParams();
    if (next.search) params.set("search", next.search);
    if (next.status) params.set("status", next.status);
    if (next.priority) params.set("priority", next.priority);
    if (next.page && next.page !== "0") params.set("page", next.page);
    setSearchParams(params);
  }

  const totalPages = page?.totalPages ?? 0;
  return (
    <section className="page-content">
      <div className="users-title-row">
        <div>
          <p className="eyebrow">ATENDIMENTO</p>
          <h1>Chamados</h1>
          <p className="page-lead">
            Acompanhe solicitações da sua organização.
          </p>
        </div>
        <Link className="button button--primary" to="/tickets/new">
          Novo chamado
        </Link>
      </div>
      <form
        className="asset-filters ticket-filters"
        onSubmit={(event) => {
          event.preventDefault();
          updateFilters({ ...filters, search: searchInput.trim() });
        }}
      >
        <label>
          Buscar por título
          <input
            aria-label="Buscar chamado"
            value={searchInput}
            maxLength={160}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Título do chamado"
          />
        </label>
        <label>
          Status
          <select
            aria-label="Filtrar chamados por status"
            value={filters.status}
            onChange={(event) =>
              updateFilters({ ...filters, status: event.target.value })
            }
          >
            <option value="">Todos os status</option>
            <option value="OPEN">Aberto</option>
            <option value="IN_PROGRESS">Em andamento</option>
            <option value="PENDING_APPROVAL">Aguardando aprovação</option>
            <option value="RESOLVED">Resolvido</option>
            <option value="CLOSED">Fechado</option>
          </select>
        </label>
        <label>
          Prioridade
          <select
            aria-label="Filtrar chamados por prioridade"
            value={filters.priority}
            onChange={(event) =>
              updateFilters({ ...filters, priority: event.target.value })
            }
          >
            <option value="">Todas as prioridades</option>
            <option value="LOW">Baixa</option>
            <option value="MEDIUM">Média</option>
            <option value="HIGH">Alta</option>
          </select>
        </label>
        <button className="button button--quiet" type="submit">
          Buscar
        </button>
      </form>
      {loading && <p role="status">Carregando chamados…</p>}
      {error && (
        <div className="notice notice--error" role="alert">
          {error}{" "}
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
            <h2>Solicitações</h2>
            <span>
              {page.totalElements}{" "}
              {page.totalElements === 1 ? "chamado" : "chamados"}
            </span>
          </div>
          {page.content.length === 0 ? (
            <p className="empty-state">
              {filters.search || filters.status || filters.priority
                ? "Nenhum chamado corresponde aos filtros."
                : "Você ainda não tem chamados. Abra um novo chamado para começar."}
            </p>
          ) : (
            <div className="table-scroll">
              <table className="ticket-table">
                <thead>
                  <tr>
                    <th>Chamado</th>
                    <th>Status</th>
                    <th>Prioridade</th>
                    <th>Solicitante</th>
                    <th>Atualizado</th>
                  </tr>
                </thead>
                <tbody>
                  {page.content.map((ticket) => (
                    <tr key={ticket.id}>
                      <td>
                        <Link
                          className="ticket-title-link"
                          to={`/tickets/${ticket.id}`}
                        >
                          #{ticket.id} · {ticket.title}
                        </Link>
                      </td>
                      <td>
                        <span
                          className={`status-tag ticket-status ticket-status--${ticket.status.toLowerCase()}`}
                        >
                          {statusName(ticket.status)}
                        </span>
                      </td>
                      <td>{priorityName(ticket.priority)}</td>
                      <td>{ticket.requesterName}</td>
                      <td>{formatDate(ticket.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="pagination" aria-label="Paginação de chamados">
            <span>
              Página {totalPages === 0 ? 0 : pageNumber + 1} de{" "}
              {Math.max(totalPages, 1)}
            </span>
            <div>
              <button
                className="button button--quiet"
                onClick={() =>
                  updateFilters({
                    ...filters,
                    page: String(Math.max(0, pageNumber - 1)),
                  })
                }
                disabled={loading || pageNumber === 0}
              >
                Anterior
              </button>
              <button
                className="button button--quiet"
                onClick={() =>
                  updateFilters({ ...filters, page: String(pageNumber + 1) })
                }
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

function TicketCreatePage() {
  const navigate = useNavigate();
  const [assets, setAssets] = useState<Asset[]>([]);
  const [assetsLoading, setAssetsLoading] = useState(true);
  const [assetError, setAssetError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({
    title: "",
    description: "",
    priority: "MEDIUM" as TicketPriority,
    assetId: "",
  });

  useEffect(() => {
    let active = true;
    request<AssetPage>("/assets?status=ACTIVE&page=0&size=100")
      .then((result) => {
        if (active) setAssets(result.content);
      })
      .catch(() => {
        if (active)
          setAssetError(
            "Não foi possível carregar os ativos. Você ainda pode abrir o chamado sem associar um ativo.",
          );
      })
      .finally(() => {
        if (active) setAssetsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function createTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const ticket = await request<Ticket>("/tickets", {
        method: "POST",
        body: JSON.stringify({
          title: form.title.trim(),
          description: form.description.trim(),
          priority: form.priority,
          ...(form.assetId ? { assetId: Number(form.assetId) } : {}),
        }),
      });
      navigate(`/tickets/${ticket.id}`, {
        state: { notice: "Chamado aberto com sucesso." },
      });
    } catch (cause) {
      setError(
        cause instanceof ApiError
          ? cause.detail
          : "Não foi possível abrir o chamado. Tente novamente.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="page-content">
      <p className="eyebrow">ATENDIMENTO</p>
      <h1>Novo chamado</h1>
      <p className="page-lead">
        Descreva sua solicitação para que a equipe possa ajudar.
      </p>
      <form className="user-form-card ticket-form" onSubmit={createTicket}>
        {error && (
          <div className="notice notice--error" role="alert">
            {error}
          </div>
        )}
        {assetError && (
          <div className="notice notice--warning" role="status">
            {assetError}
          </div>
        )}
        <div className="user-form-grid">
          <label className="ticket-title-field">
            Título
            <input
              name="title"
              value={form.title}
              maxLength={160}
              onChange={(event) =>
                setForm({ ...form, title: event.target.value })
              }
              required
            />
          </label>
          <label>
            Prioridade
            <select
              name="priority"
              value={form.priority}
              onChange={(event) =>
                setForm({
                  ...form,
                  priority: event.target.value as TicketPriority,
                })
              }
            >
              <option value="LOW">Baixa</option>
              <option value="MEDIUM">Média</option>
              <option value="HIGH">Alta</option>
            </select>
          </label>
          <label>
            Ativo relacionado (opcional)
            <select
              name="assetId"
              value={form.assetId}
              onChange={(event) =>
                setForm({ ...form, assetId: event.target.value })
              }
              disabled={assetsLoading || assets.length === 0}
            >
              <option value="">
                {assetsLoading
                  ? "Carregando ativos…"
                  : assets.length
                    ? "Nenhum ativo"
                    : "Sem ativos disponíveis"}
              </option>
              {assets.map((asset) => (
                <option key={asset.id} value={asset.id}>
                  {asset.assetNumber} · {asset.name}
                </option>
              ))}
            </select>
          </label>
          <label className="ticket-description-field">
            Descrição
            <textarea
              name="description"
              value={form.description}
              maxLength={4000}
              rows={7}
              onChange={(event) =>
                setForm({ ...form, description: event.target.value })
              }
              required
            />
          </label>
        </div>
        <div className="user-form-actions">
          <Link className="button button--quiet" to="/tickets">
            Cancelar
          </Link>
          <button
            className="button button--primary"
            type="submit"
            disabled={saving}
          >
            {saving ? "Abrindo…" : "Abrir chamado"}
          </button>
        </div>
      </form>
    </section>
  );
}

function TicketDetailPage() {
  const { ticketId } = useParams();
  const location = useLocation();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState(
    (location.state as { notice?: string } | null)?.notice ?? null,
  );
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    request<Ticket>(`/tickets/${ticketId}`)
      .then((result) => {
        if (active) setTicket(result);
      })
      .catch((cause: unknown) => {
        if (active)
          setError(
            cause instanceof ApiError && cause.status === 404
              ? "Chamado não encontrado ou indisponível para sua conta."
              : cause instanceof ApiError
                ? cause.detail
                : "Não foi possível carregar o chamado.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [ticketId, reloadToken]);

  return (
    <section className="page-content">
      <Link className="back-link" to="/tickets">
        ← Voltar aos chamados
      </Link>
      {loading && <p role="status">Carregando chamado…</p>}
      {error && (
        <div className="notice notice--error" role="alert">
          {error}{" "}
          <button
            className="text-button"
            onClick={() => setReloadToken((token) => token + 1)}
          >
            Tentar novamente
          </button>
        </div>
      )}
      {notice && (
        <div className="notice notice--success" role="status">
          {notice}
          <button className="text-button" onClick={() => setNotice(null)}>
            Fechar
          </button>
        </div>
      )}
      {ticket && (
        <article className="ticket-detail-card">
          <div className="ticket-detail-heading">
            <div>
              <p className="eyebrow">CHAMADO #{ticket.id}</p>
              <h1>{ticket.title}</h1>
            </div>
            <span
              className={`status-tag ticket-status ticket-status--${ticket.status.toLowerCase()}`}
            >
              {statusName(ticket.status)}
            </span>
          </div>
          <p className="ticket-description">{ticket.description}</p>
          <dl className="ticket-metadata">
            <div>
              <dt>Solicitante</dt>
              <dd>{ticket.requesterName}</dd>
            </div>
            <div>
              <dt>Responsável</dt>
              <dd>{ticket.assignedAgentName ?? "Ainda não atribuído"}</dd>
            </div>
            <div>
              <dt>Ativo</dt>
              <dd>{ticket.assetNumber ?? "Nenhum ativo associado"}</dd>
            </div>
            <div>
              <dt>Prioridade</dt>
              <dd>{priorityName(ticket.priority)}</dd>
            </div>
            <div>
              <dt>Aberto em</dt>
              <dd>{formatDate(ticket.createdAt)}</dd>
            </div>
            <div>
              <dt>Última atualização</dt>
              <dd>{formatDate(ticket.updatedAt)}</dd>
            </div>
            <div>
              <dt>Prazo SLA</dt>
              <dd>{formatDate(ticket.dueAt)}</dd>
            </div>
            {ticket.resolvedAt && (
              <div>
                <dt>Resolvido em</dt>
                <dd>{formatDate(ticket.resolvedAt)}</dd>
              </div>
            )}
            {ticket.closedAt && (
              <div>
                <dt>Fechado em</dt>
                <dd>{formatDate(ticket.closedAt)}</dd>
              </div>
            )}
          </dl>
        </article>
      )}
    </section>
  );
}

function statusName(status: TicketStatus) {
  return {
    OPEN: "Aberto",
    IN_PROGRESS: "Em andamento",
    PENDING_APPROVAL: "Aguardando aprovação",
    RESOLVED: "Resolvido",
    CLOSED: "Fechado",
  }[status];
}

function priorityName(priority: TicketPriority) {
  return { LOW: "Baixa", MEDIUM: "Média", HIGH: "Alta" }[priority];
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function AssetsPage() {
  const { user } = useAuth();
  const canManage = user?.role === "ADMIN" || user?.role === "AGENT";
  const [page, setPage] = useState<AssetPage | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [reloadToken, setReloadToken] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [filters, setFilters] = useState({
    search: "",
    type: "",
    status: "ACTIVE" as AssetStatus,
  });
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Asset | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyAssetId, setBusyAssetId] = useState<number | null>(null);
  const [form, setForm] = useState({
    assetNumber: "",
    name: "",
    type: "",
    description: "",
    status: "ACTIVE" as AssetStatus,
  });

  useEffect(() => {
    let active = true;
    const query = new URLSearchParams({ page: String(pageNumber), size: "20" });
    if (filters.search) query.set("search", filters.search);
    if (filters.type) query.set("type", filters.type);
    query.set("status", canManage ? filters.status : "ACTIVE");
    setLoading(true);
    setListError(null);
    request<AssetPage>(`/assets?${query.toString()}`)
      .then((result) => {
        if (active) setPage(result);
      })
      .catch((cause: unknown) => {
        if (active) {
          setListError(
            cause instanceof ApiError
              ? cause.status === 403
                ? "Sua conta não tem acesso aos ativos desta organização."
                : cause.detail
              : "Não foi possível carregar os ativos. Tente novamente.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [canManage, filters, pageNumber, reloadToken]);

  function openCreate() {
    setEditing(null);
    setForm({
      assetNumber: "",
      name: "",
      type: "",
      description: "",
      status: "ACTIVE",
    });
    setFormError(null);
    setFormOpen(true);
    setNotice(null);
  }

  function openEdit(asset: Asset) {
    setEditing(asset);
    setForm({
      assetNumber: asset.assetNumber,
      name: asset.name,
      type: asset.type,
      description: asset.description ?? "",
      status: asset.status,
    });
    setFormError(null);
    setFormOpen(true);
    setNotice(null);
  }

  async function saveAsset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const input = {
      assetNumber: form.assetNumber.trim(),
      name: form.name.trim(),
      type: form.type.trim(),
      description: form.description.trim(),
      status: form.status,
    };
    if (!input.assetNumber || !input.name || !input.type) {
      setFormError("Informe patrimônio, nome e tipo do ativo.");
      return;
    }
    if (
      editing?.status === "ACTIVE" &&
      input.status === "INACTIVE" &&
      !window.confirm(`Deseja inativar o ativo ${editing.assetNumber}?`)
    )
      return;
    setSaving(true);
    try {
      await request(editing ? `/assets/${editing.id}` : "/assets", {
        method: editing ? "PATCH" : "POST",
        body: JSON.stringify(input),
      });
      setNotice(
        editing
          ? "As alterações do ativo foram salvas."
          : "O ativo foi cadastrado.",
      );
      setFormOpen(false);
      setEditing(null);
      setPageNumber(0);
      setReloadToken((token) => token + 1);
    } catch (cause) {
      setFormError(
        cause instanceof ApiError
          ? cause.status === 409
            ? "Já existe um ativo com esse patrimônio nesta organização."
            : cause.detail
          : "Não foi possível salvar o ativo. Tente novamente.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function toggleAsset(asset: Asset) {
    const deactivate = asset.status === "ACTIVE";
    if (
      deactivate &&
      !window.confirm(`Deseja inativar o ativo ${asset.assetNumber}?`)
    )
      return;
    setNotice(null);
    setBusyAssetId(asset.id);
    try {
      await request(`/assets/${asset.id}`, {
        method: "PATCH",
        body: JSON.stringify({ status: deactivate ? "INACTIVE" : "ACTIVE" }),
      });
      setNotice(
        deactivate
          ? `O ativo ${asset.assetNumber} foi inativado.`
          : `O ativo ${asset.assetNumber} foi reativado.`,
      );
      setReloadToken((token) => token + 1);
    } catch (cause) {
      setNotice(
        cause instanceof ApiError
          ? cause.detail
          : "Não foi possível atualizar o ativo.",
      );
    } finally {
      setBusyAssetId(null);
    }
  }

  const totalPages = page?.totalPages ?? 0;
  return (
    <section className="page-content">
      <div className="users-title-row">
        <div>
          <p className="eyebrow">INVENTÁRIO</p>
          <h1>Ativos</h1>
          <p className="page-lead">
            {canManage
              ? "Consulte e mantenha os ativos da sua organização."
              : "Ativos disponíveis para associar a um chamado."}
          </p>
        </div>
        {canManage && (
          <button className="button button--primary" onClick={openCreate}>
            Novo ativo
          </button>
        )}
      </div>
      {notice && (
        <div className="notice notice--success" role="status">
          {notice}
        </div>
      )}
      {canManage && formOpen && (
        <form className="user-form-card" onSubmit={saveAsset}>
          <div className="user-form-heading">
            <div>
              <p className="eyebrow">INVENTÁRIO</p>
              <h2>{editing ? "Editar ativo" : "Cadastrar ativo"}</h2>
            </div>
            <button
              className="button button--quiet"
              type="button"
              onClick={() => setFormOpen(false)}
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
              Número patrimonial
              <input
                name="assetNumber"
                value={form.assetNumber}
                maxLength={80}
                onChange={(event) =>
                  setForm({ ...form, assetNumber: event.target.value })
                }
                required
              />
            </label>
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
              Tipo
              <input
                name="type"
                value={form.type}
                maxLength={80}
                onChange={(event) =>
                  setForm({ ...form, type: event.target.value })
                }
                required
              />
            </label>
            <label>
              Estado
              <select
                name="status"
                value={form.status}
                onChange={(event) =>
                  setForm({
                    ...form,
                    status: event.target.value as AssetStatus,
                  })
                }
              >
                <option value="ACTIVE">Ativo</option>
                <option value="INACTIVE">Inativo</option>
              </select>
            </label>
            <label className="asset-description-field">
              Descrição
              <textarea
                name="description"
                value={form.description}
                maxLength={2000}
                rows={3}
                onChange={(event) =>
                  setForm({ ...form, description: event.target.value })
                }
              />
            </label>
          </div>
          <div className="user-form-actions">
            <button
              className="button button--quiet"
              type="button"
              onClick={() => setFormOpen(false)}
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
                : editing
                  ? "Salvar alterações"
                  : "Cadastrar"}
            </button>
          </div>
        </form>
      )}
      <form
        className="asset-filters"
        onSubmit={(event) => {
          event.preventDefault();
          setPageNumber(0);
          setFilters({ ...filters, search: searchInput.trim() });
        }}
      >
        <label>
          Buscar ativo
          <input
            aria-label="Buscar ativo"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Patrimônio ou nome"
          />
        </label>
        <label>
          Tipo
          <input
            aria-label="Filtrar por tipo"
            value={filters.type}
            onChange={(event) => {
              setPageNumber(0);
              setFilters({ ...filters, type: event.target.value.trim() });
            }}
            placeholder="Todos os tipos"
          />
        </label>
        {canManage && (
          <label>
            Estado
            <select
              aria-label="Filtrar por estado"
              value={filters.status}
              onChange={(event) => {
                setPageNumber(0);
                setFilters({
                  ...filters,
                  status: event.target.value as AssetStatus,
                });
              }}
            >
              <option value="ACTIVE">Ativos</option>
              <option value="INACTIVE">Inativos</option>
            </select>
          </label>
        )}
        <button className="button button--quiet" type="submit">
          Buscar
        </button>
      </form>
      {loading && <p role="status">Carregando ativos…</p>}
      {listError && (
        <div className="notice notice--error" role="alert">
          {listError}{" "}
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
            <h2>
              {canManage ? "Inventário da organização" : "Ativos disponíveis"}
            </h2>
            <span>
              {page.totalElements}{" "}
              {page.totalElements === 1 ? "ativo" : "ativos"}
            </span>
          </div>
          {page.content.length === 0 ? (
            <p className="empty-state">
              {filters.search || filters.type || filters.status
                ? "Nenhum ativo corresponde aos filtros."
                : canManage
                  ? "Ainda não há ativos nesta organização."
                  : "Nenhum ativo ativo está disponível no momento."}
            </p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Número patrimonial</th>
                    <th>Nome</th>
                    <th>Tipo</th>
                    {canManage && (
                      <>
                        <th>Estado</th>
                        <th>Ações</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {page.content.map((asset) => (
                    <tr key={asset.id}>
                      <td>{asset.assetNumber}</td>
                      <td>{asset.name}</td>
                      <td>{asset.type}</td>
                      {canManage && (
                        <>
                          <td>
                            <span
                              className={`status-tag ${asset.status === "ACTIVE" ? "status-tag--active" : ""}`}
                            >
                              {asset.status === "ACTIVE" ? "Ativo" : "Inativo"}
                            </span>
                          </td>
                          <td className="user-row-actions">
                            <div>
                              <button
                                className="text-button"
                                onClick={() => openEdit(asset)}
                              >
                                Editar
                              </button>
                              <button
                                className={`text-button ${asset.status === "ACTIVE" ? "text-button--danger" : ""}`}
                                onClick={() => void toggleAsset(asset)}
                                disabled={busyAssetId === asset.id}
                              >
                                {busyAssetId === asset.id
                                  ? "Salvando…"
                                  : asset.status === "ACTIVE"
                                    ? "Inativar"
                                    : "Reativar"}
                              </button>
                            </div>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="pagination" aria-label="Paginação de ativos">
            <span>
              Página {page.totalPages === 0 ? 0 : pageNumber + 1} de{" "}
              {Math.max(totalPages, 1)}
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
