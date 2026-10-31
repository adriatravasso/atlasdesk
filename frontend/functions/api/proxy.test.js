import assert from "node:assert/strict";
import { test } from "node:test";
import { onRequest } from "./[[path]].js";

test("forwards method, query, cookies and headers to the fixed HTTPS origin", async (context) => {
  let forwarded;
  context.mock.method(globalThis, "fetch", async (request) => {
    forwarded = request;
    return new Response("ready", { status: 200 });
  });

  const response = await onRequest({
    request: new Request("https://atlasdesk.pages.dev/api/tickets?page=2", {
      headers: {
        cookie: "JSESSIONID=session; XSRF-TOKEN=csrf",
        "x-xsrf-token": "csrf",
      },
    }),
    env: { ATLASDESK_API_ORIGIN: "https://atlasdesk-api.onrender.com" },
  });

  assert.equal(
    forwarded.url,
    "https://atlasdesk-api.onrender.com/api/tickets?page=2",
  );
  assert.equal(forwarded.method, "GET");
  assert.equal(
    forwarded.headers.get("cookie"),
    "JSESSIONID=session; XSRF-TOKEN=csrf",
  );
  assert.equal(forwarded.headers.get("x-xsrf-token"), "csrf");
  assert.equal(await response.text(), "ready");
});

test("forwards JSON body and preserves upstream status and session cookies", async (context) => {
  let forwarded;
  context.mock.method(globalThis, "fetch", async (request) => {
    forwarded = request;
    return new Response(JSON.stringify({ user: { id: 3 } }), {
      status: 201,
      headers: {
        "content-type": "application/json",
        "set-cookie":
          "JSESSIONID=new-session; Path=/; HttpOnly; Secure; SameSite=Lax",
      },
    });
  });

  const response = await onRequest({
    request: new Request("https://atlasdesk.pages.dev/api/auth/login", {
      method: "POST",
      headers: { "content-type": "application/json", "x-xsrf-token": "csrf" },
      body: JSON.stringify({ organizationId: 3, email: "demo@example.test" }),
    }),
    env: { ATLASDESK_API_ORIGIN: "https://atlasdesk-api.onrender.com" },
  });

  assert.equal(forwarded.method, "POST");
  assert.equal(forwarded.headers.get("x-xsrf-token"), "csrf");
  assert.equal(
    await forwarded.text(),
    '{"organizationId":3,"email":"demo@example.test"}',
  );
  assert.equal(response.status, 201);
  assert.match(
    response.headers.get("set-cookie"),
    /HttpOnly; Secure; SameSite=Lax/,
  );
  assert.deepEqual(await response.json(), { user: { id: 3 } });
});

test("rejects unsafe origins and paths instead of becoming an open proxy", async (context) => {
  let called = false;
  context.mock.method(globalThis, "fetch", async () => {
    called = true;
    return new Response("unexpected");
  });

  const notFound = await onRequest({
    request: new Request("https://atlasdesk.pages.dev/outside-api"),
    env: { ATLASDESK_API_ORIGIN: "https://atlasdesk-api.onrender.com" },
  });
  const unsafeOrigin = await onRequest({
    request: new Request("https://atlasdesk.pages.dev/api/health"),
    env: { ATLASDESK_API_ORIGIN: "http://localhost:8080" },
  });

  assert.equal(notFound.status, 404);
  assert.equal(unsafeOrigin.status, 503);
  assert.equal(called, false);
});

test("returns a bounded gateway error when the backend cannot be reached", async (context) => {
  context.mock.method(globalThis, "fetch", async () => {
    throw new Error("backend is sleeping");
  });

  const response = await onRequest({
    request: new Request("https://atlasdesk.pages.dev/api/health"),
    env: { ATLASDESK_API_ORIGIN: "https://atlasdesk-api.onrender.com" },
  });

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: "api_unavailable" });
});
