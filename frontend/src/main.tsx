import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import { BackendStatusProvider } from "./backend-status";
import { AuthProvider } from "./AuthContext";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BackendStatusProvider>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BackendStatusProvider>
  </React.StrictMode>,
);
