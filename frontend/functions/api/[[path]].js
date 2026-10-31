const SERVICE_UNAVAILABLE = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function jsonError(status, error) {
  return new Response(JSON.stringify({ error }), {
    status,
    headers: SERVICE_UNAVAILABLE,
  });
}

function apiOrigin(value) {
  if (!value) return null;
  try {
    const origin = new URL(value);
    if (
      origin.protocol !== "https:" ||
      origin.username ||
      origin.password ||
      origin.pathname !== "/" ||
      origin.search ||
      origin.hash
    ) {
      return null;
    }
    return origin;
  } catch {
    return null;
  }
}

export async function onRequest({ request, env }) {
  const incoming = new URL(request.url);
  if (incoming.pathname !== "/api" && !incoming.pathname.startsWith("/api/")) {
    return jsonError(404, "not_found");
  }

  const origin = apiOrigin(env.ATLASDESK_API_ORIGIN);
  if (!origin) return jsonError(503, "api_origin_not_configured");

  const target = new URL(`${incoming.pathname}${incoming.search}`, origin);
  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.delete("content-length");

  try {
    const hasBody = !["GET", "HEAD"].includes(request.method.toUpperCase());
    const upstreamRequest = new Request(target, {
      method: request.method,
      headers,
      body: hasBody ? await request.arrayBuffer() : undefined,
      redirect: "manual",
    });
    const upstream = await fetch(upstreamRequest);
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: upstream.headers,
    });
  } catch {
    return jsonError(502, "api_unavailable");
  }
}
