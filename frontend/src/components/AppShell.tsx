import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function Brand() {
  return (
    <div className="brand" aria-label="Precued">
      <span className="brand-mark">P</span>
      <span className="brand-name">Precued</span>
    </div>
  );
}

export function AppShell({ children, showTaglines = true }: { children: ReactNode; showTaglines?: boolean }) {
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/templates" className="brand-link">
          <Brand />
        </Link>
        <nav className="topnav" aria-label="Main navigation">
          <Link to="/templates">▧ <span>Templates</span></Link>
          <span>▣ <span>Rooms</span></span>
          <span>ⓘ <span>Help</span></span>
        </nav>
        <div className="topbar-actions">
          <button className="secondary-button compact">Get in touch</button>
          <button className="avatar-button" aria-label="Account menu">JD⌄</button>
        </div>
      </header>
      <main className="page-background">
        {showTaglines && <div className="side-tagline left-tagline">CONVERSATIONS<br />DRIVE PROGRESS<span /></div>}
        {children}
        {showTaglines && <div className="side-tagline right-tagline">THE<br />RIGHT<br />PEOPLE<br />SEE<br />MORE<span /></div>}
      </main>
    </div>
  );
}
