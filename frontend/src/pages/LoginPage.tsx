import { FormEvent, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { validateLogIn } from "../lib/accountForms";
import { api } from "../lib/api";
import { saveAuthSession } from "../lib/session";

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  // lib/api.ts's request() redirects here (full navigation, not a client
  // route change) on any 401 from an Account Holder session, appending
  // this so the reason survives that reload.
  const [error, setError] = useState<string | null>(
    searchParams.get("sessionExpired") ? "Your session has expired. Please log in again." : null,
  );

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const validationError = validateLogIn({ email, password });
    if (validationError) {
      setError(validationError);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const session = await api.logIn(email.trim(), password);
      saveAuthSession(session);
      navigate("/profile", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to log in");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell>
      <section className="auth-page page-center-narrow">
        <div className="page-heading centered">
          <h1>Log in to Precued</h1>
          <p>Welcome back.</p>
        </div>
        <form className="surface-card auth-card" onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" required />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>
          <button className="primary-button wide" disabled={loading}>{loading ? "Logging in..." : "Log In  →"}</button>
          <small>Don't have an account? <Link to="/signup">Sign up</Link></small>
        </form>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}
