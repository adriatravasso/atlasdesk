import { useEffect, useState } from "react";

type ApiStatus = "loading" | "connected" | "unavailable";

export default function App() {
  const [apiStatus, setApiStatus] = useState<ApiStatus>("loading");

  useEffect(() => {
    const controller = new AbortController();

    fetch("/api/health", { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error("API request failed");
        return response.json() as Promise<{ status: string; service: string }>;
      })
      .then((health) => {
        setApiStatus(
          health.status === "UP" && health.service === "atlasdesk-api"
            ? "connected"
            : "unavailable",
        );
      })
      .catch(() => {
        if (!controller.signal.aborted) setApiStatus("unavailable");
      });

    return () => controller.abort();
  }, []);

  const statusText = {
    loading: "Checking API connection",
    connected: "API connected",
    unavailable: "API unavailable",
  }[apiStatus];

  return (
    <main className="page-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="AtlasDesk home">
          <span className="brand-mark" aria-hidden="true">
            A
          </span>
          <span>AtlasDesk</span>
        </a>
        <div
          className={`connection connection--${apiStatus}`}
          role="status"
          aria-live="polite"
        >
          <span className="connection-dot" aria-hidden="true" />
          {statusText}
        </div>
      </header>

      <section className="welcome" aria-labelledby="welcome-title">
        <p className="eyebrow">IT service management</p>
        <h1 id="welcome-title">A clearer view of IT operations.</h1>
        <p className="intro">
          AtlasDesk brings service requests and technology assets together,
          giving your team a dependable place to keep work moving.
        </p>
        <div className="welcome-footer">
          <span className="welcome-line" aria-hidden="true" />
          <span>Built for focused, reliable service</span>
        </div>
      </section>

      <footer className="page-footer">
        <span>AtlasDesk</span>
        <span>IT services, in one place.</span>
      </footer>
    </main>
  );
}
