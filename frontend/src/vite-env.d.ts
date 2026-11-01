/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DEMO_ENABLED?: string;
  readonly VITE_DEMO_ORGANIZATION_ID?: string;
  readonly VITE_DEMO_EMAIL?: string;
  readonly VITE_DEMO_PASSWORD?: string;
}

declare const __APP_VERSION__: string;
