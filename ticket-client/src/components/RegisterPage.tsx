import { type FormEvent, useState } from "react";

import { register } from "../api/authApi";

interface RegisterPageProps {
  onRegistered: () => void;
  onLoginClick: () => void;
}

export function RegisterPage({
  onRegistered,
  onLoginClick,
}: RegisterPageProps) {
  const [email, setEmail] = useState("");

  const [username, setUsername] = useState("");

  const [firstName, setFirstName] = useState("");

  const [lastName, setLastName] = useState("");

  const [password, setPassword] = useState("");

  const [repeatPassword, setRepeatPassword] = useState("");

  const [error, setError] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    setError(null);

    if (!email.trim()) {
      setError("Email is required");
      return;
    }

    if (username.trim().length < 3) {
      setError("Username must contain at least 3 characters");
      return;
    }

    if (password.length < 8) {
      setError("Password must contain at least 8 characters");
      return;
    }

    if (password !== repeatPassword) {
      setError("Passwords do not match");
      return;
    }

    try {
      setLoading(true);

      const response = await register({
        email: email.trim(),
        username: username.trim(),
        password,
        firstName: firstName.trim() || undefined,
        lastName: lastName.trim() || undefined,
      });

      console.log("Registered user:", response.userId, response.status);

      onRegistered();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card register-card">
        <div className="login-brand">
          <span>Snezhnaya Railway</span>

          <h1>Create account</h1>

          <p>Register to manage railway tickets and bookings.</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="register-name-row">
            <label className="form-field">
              <span>First name</span>

              <input
                type="text"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
              />
            </label>

            <label className="form-field">
              <span>Last name</span>

              <input
                type="text"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
              />
            </label>
          </div>

          <label className="form-field">
            <span>Email</span>

            <input
              type="email"
              value={email}
              autoComplete="email"
              placeholder="user@example.com"
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>

          <label className="form-field">
            <span>Username</span>

            <input
              type="text"
              value={username}
              autoComplete="username"
              placeholder="snowtraveler"
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>

          <label className="form-field">
            <span>Password</span>

            <input
              type="password"
              value={password}
              autoComplete="new-password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          <label className="form-field">
            <span>Repeat password</span>

            <input
              type="password"
              value={repeatPassword}
              autoComplete="new-password"
              onChange={(event) => setRepeatPassword(event.target.value)}
            />
          </label>

          {error && <div className="booking-alert error">{error}</div>}

          <button type="submit" className="login-button" disabled={loading}>
            {loading ? "Creating account..." : "Create account"}
          </button>

          <button
            type="button"
            className="auth-switch-button"
            onClick={onLoginClick}
          >
            Already have an account? Sign in
          </button>
        </form>
      </section>
    </main>
  );
}
