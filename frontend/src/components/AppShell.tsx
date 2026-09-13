import type { ReactNode } from "react";
import { useState } from "react";
import { Link, useMatch, useNavigate } from "react-router-dom";
import { initials } from "../lib/initials";
import { clearSession, getAuthSession } from "../lib/session";
import { SessionFlowCallDock } from "./SessionFlowCallDock";

export function Brand() {
  return (
    <div className="brand" aria-label="Precued">
      <span className="brand-mark">P</span>
      <span className="brand-name">Precued</span>
    </div>
  );
}

export function AppShell({ children, showTaglines = true }: { children: ReactNode; showTaglines?: boolean }) {
  const navigate = useNavigate();
  const callMatch = useMatch("/rooms/:roomId/call");
  const auth = getAuthSession();
  const [menuOpen, setMenuOpen] = useState(false);

  function logOut() {
    clearSession();
    setMenuOpen(false);
    navigate("/", { replace: true });
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to={auth ? "/profile" : "/"} className="brand-link">
          <Brand />
        </Link>
        <nav className="topnav" aria-label="Main navigation">
          <Link to="/templates">▧ <span>Templates</span></Link>
          <span>▣ <span>Rooms</span></span>
          <span>ⓘ <span>Help</span></span>
        </nav>
        <div className="topbar-actions">
          <button className="secondary-button compact">Get in touch</button>
          {auth && (
            <div className="account-menu-wrap">
              <button className="avatar-button" aria-label="Account menu" onClick={() => setMenuOpen(!menuOpen)}>
                {initials(auth.displayName)}⌄
              </button>
              {menuOpen && (
                <div className="account-menu">
                  <Link to="/profile" onClick={() => setMenuOpen(false)}>Profile</Link>
                  <button type="button" onClick={logOut}>Log out</button>
                </div>
              )}
            </div>
          )}
        </div>
      </header>
      <main className="page-background">
        {!showTaglines && callMatch?.params.roomId && <SessionFlowCallDock roomId={callMatch.params.roomId} />}
        {showTaglines && <div className="side-tagline left-tagline">CONVERSATIONS<br />DRIVE PROGRESS<span /></div>}
        {children}
        {showTaglines && <div className="side-tagline right-tagline">THE<br />RIGHT<br />PEOPLE<br />SEE<br />MORE<span /></div>}
      </main>
    </div>
  );
}
