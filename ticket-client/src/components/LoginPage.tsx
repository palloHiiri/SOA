import { type FormEvent, useState } from "react";

import { login } from "../api/authApi";

interface LoginPageProps {
  onLogin: () => void;
  onRegisterClick?: () => void;
}

export function LoginPage({ onLogin, onRegisterClick }: LoginPageProps) {
  const [loginValue, setLoginValue] = useState("");

  const [password, setPassword] = useState("");

  const [error, setError] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!loginValue.trim()) {
      setError("Login is required");
      return;
    }

    if (!password) {
      setError("Password is required");
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const response = await login({
        login: loginValue.trim(),
        password,
      });

      if (response.status === "MFA_REQUIRED") {
        setError("MFA is required for this account");

        return;
      }

      onLogin();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <span>Snezhnaya Railway</span>

          <h1>Welcome to Snezhnaya</h1>

        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label className="form-field">
            <span>Username or email</span>

            <input
              type="text"
              value={loginValue}
              autoComplete="username"
              placeholder="agent name or email"
              onChange={(event) => setLoginValue(event.target.value)}
            />
          </label>

          <label className="form-field">
            <span>Password</span>

            <input
              type="password"
              value={password}
              autoComplete="current-password"
              placeholder="••••••••"
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          {error && <div className="booking-alert error">{error}</div>}

          <button type="submit" className="login-button" disabled={loading}>
            {loading ? "Signing in..." : "Sign in"}
          </button>
          <button
            type="button"
            className="auth-switch-button"
            onClick={onRegisterClick}
          >
            First time here? Become a fatui agent
          </button>
        </form>
      </section>
    </main>
  );
}
