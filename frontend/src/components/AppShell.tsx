import type { ReactNode } from "react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { navigateToProfile } from "../lib/accountMenuNavigation";
import { PRIMARY_NAVIGATION, WORKSPACE_NAVIGATION, type AppNavigationItem } from "../lib/appNavigation";
import { initials } from "../lib/initials";
import { clearSession, getAuthSession } from "../lib/session";

export function Brand() {
  return (
    <div className="brand" aria-label="Precued">
      <span className="brand-mark" aria-hidden="true">
        <span className="brand-mark-grid">
          <span className="brand-mark-cell" />
          <span className="brand-mark-cell" />
          <span className="brand-mark-cell" />
          <span className="brand-mark-cell" />
          <span className="brand-mark-cell" />
        </span>
      </span>
      <span className="brand-name">Precued</span>
    </div>
  );
}

function NavigationItems({ items, className }: { items: AppNavigationItem[]; className: string }) {
  return (
    <nav className={className} aria-label={className === "topnav" ? "Main navigation" : "Workspace navigation"}>
      {items.map((item) => item.href ? (
        <Link key={item.label} to={item.href}>
          <span aria-hidden="true">{item.icon}</span>
          <span>{item.label}</span>
        </Link>
      ) : (
        <span key={item.label} className="nav-item-disabled" aria-disabled="true">
          <span aria-hidden="true">{item.icon}</span>
          <span>{item.label}</span>
        </span>
      ))}
    </nav>
  );
}

export function WorkspaceShell({ children }: { children: ReactNode }) {
  const auth = getAuthSession();
  return (
    <div className="workspace-shell">
      <aside className="workspace-sidebar">
        <Link to={auth ? "/profile" : "/"} className="workspace-brand-link">
          <Brand />
        </Link>
        <NavigationItems items={WORKSPACE_NAVIGATION} className="workspace-nav" />
        <div className="workspace-sidebar-footer">Structure turns conversation into progress.</div>
      </aside>
      <div className="workspace-content">{children}</div>
    </div>
  );
}

export function AppShell({ children, showNavigation = true }: { children: ReactNode; showNavigation?: boolean }) {
  const navigate = useNavigate();
  const auth = getAuthSession();
  const [menuOpen, setMenuOpen] = useState(false);

  function logOut() {
    clearSession();
    setMenuOpen(false);
    navigate("/", { replace: true });
  }

  return (
    <div className={`app-shell ${showNavigation ? "" : "app-shell-bare"}`.trim()}>
      {showNavigation && (
        <header className="topbar">
          <Link to={auth ? "/profile" : "/"} className="brand-link">
            <Brand />
          </Link>
          <NavigationItems items={PRIMARY_NAVIGATION} className="topnav" />
          <div className="topbar-actions">
            <button className="secondary-button compact">Get in touch</button>
            {auth && (
              <div className="account-menu-wrap">
                <button className="avatar-button" aria-label="Account menu" onClick={() => setMenuOpen(!menuOpen)}>
                  {initials(auth.displayName)}⌄
                </button>
                {menuOpen && (
                  <div className="account-menu">
                    <button
                      type="button"
                      onClick={() => navigateToProfile(navigate, () => setMenuOpen(false))}
                    >
                      Profile
                    </button>
                    <button type="button" onClick={logOut}>Log out</button>
                  </div>
                )}
              </div>
            )}
          </div>
        </header>
      )}
      <main className="page-background">{children}</main>
    </div>
  );
}
