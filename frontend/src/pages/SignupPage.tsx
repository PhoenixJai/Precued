import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { validateSignUp } from "../lib/accountForms";
import { api } from "../lib/api";
import { saveAuthSession } from "../lib/session";

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const validationError = validateSignUp({ email, password, displayName });
    if (validationError) {
      setError(validationError);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const session = await api.signUp(email.trim(), password, displayName.trim());
      saveAuthSession(session);
      navigate("/profile", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to sign up");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell>
      <section className="auth-page page-center-narrow">
        <div className="page-heading centered">
          <h1>Create your account</h1>
          <p>Build templates, start rooms, and manage your sessions.</p>
        </div>
        <form className="surface-card auth-card" onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" required />
          </label>
          <label>
            Display name
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Alex Chen" required />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" required />
          </label>
          <button className="primary-button wide" disabled={loading}>{loading ? "Creating account..." : "Sign Up  →"}</button>
          <small>Already have an account? <Link to="/login">Log in</Link></small>
        </form>
        {error && <div className="error-banner">{error}</div>}
      </section>
    </AppShell>
  );
}
