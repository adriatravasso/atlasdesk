export type UserRole = "ADMIN" | "AGENT" | "REQUESTER";

export type SessionUser = {
  id: number;
  organizationId: number;
  name: string;
  email: string;
  role: UserRole;
};

type CsrfResponse = { headerName: string; token: string };

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly detail: string,
    public readonly code?: string,
  ) {
    super(detail);
    this.name = "ApiError";
  }
}

let csrfRequest: Promise<CsrfResponse> | undefined;
let currentUserRequest: Promise<SessionUser> | undefined;
const unauthorizedEvent = new Event("atlasdesk:unauthorized");

function clearCsrf() {
  csrfRequest = undefined;
}

async function csrf(): Promise<CsrfResponse> {
  csrfRequest ??= fetch("/api/auth/csrf", { credentials: "same-origin" }).then(
    async (response) => {
      if (!response.ok) throw await toApiError(response);
      return (await response.json()) as CsrfResponse;
    },
  );
  try {
    return await csrfRequest;
  } catch (error) {
    clearCsrf();
    throw error;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get("content-type") ?? "";
  let detail = response.statusText || "The request could not be completed.";
  let code: string | undefined;
  if (contentType.includes("json")) {
    const body = (await response.json().catch(() => null)) as {
      detail?: string;
      title?: string;
      error?: string;
      message?: string;
    } | null;
    detail =
      body?.detail ?? body?.message ?? body?.title ?? body?.error ?? detail;
    code = body?.error;
  }
  return new ApiError(response.status, detail, code);
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = await csrf();
    headers.set(token.headerName, token.token);
  }

  const response = await fetch(`/api${path}`, {
    ...init,
    method,
    headers,
    credentials: "same-origin",
  });
  if (response.status === 401 && !["/auth/login", "/auth/me"].includes(path)) {
    window.dispatchEvent(unauthorizedEvent);
  }
  if (response.status === 403) throw await toApiError(response);
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function getCurrentUser(): Promise<SessionUser> {
  currentUserRequest ??= request<SessionUser>("/auth/me").catch(
    (error: unknown) => {
      currentUserRequest = undefined;
      throw error;
    },
  );
  return currentUserRequest;
}

export async function login(input: {
  organizationId: number;
  email: string;
  password: string;
}): Promise<SessionUser> {
  const result = await request<{ user: SessionUser }>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
  currentUserRequest = Promise.resolve(result.user);
  clearCsrf();
  return result.user;
}

export async function logout(): Promise<void> {
  await request<void>("/auth/logout", { method: "POST" });
  currentUserRequest = undefined;
  clearCsrf();
}

export function resetSessionCache() {
  currentUserRequest = undefined;
}

export function onUnauthorized(handler: () => void) {
  window.addEventListener("atlasdesk:unauthorized", handler);
  return () => window.removeEventListener("atlasdesk:unauthorized", handler);
}

export type UserPage = {
  content: Array<
    Pick<SessionUser, "id" | "name" | "email" | "role"> & { active: boolean }
  >;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AssetStatus = "ACTIVE" | "INACTIVE";

export type Asset = {
  id: number;
  organizationId: number;
  assetNumber: string;
  name: string;
  type: string;
  description: string | null;
  status: AssetStatus;
  createdAt: string;
  updatedAt: string;
};

export type AssetPage = {
  content: Asset[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
