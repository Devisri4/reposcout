import { Github, ScanSearch } from 'lucide-react';

const SOURCE = 'https://github.com/Devisri4/reposcout';

export default function Navbar({ server }) {
  return (
    <header className="nav">
      <div className="nav-inner">
        <a className="brand" href="#top" aria-label="RepoScout home">
          <span className="brand-icon" aria-hidden="true">
            <ScanSearch size={20} />
          </span>
          <span className="brand-name">
            Repo<span>Scout</span>
          </span>
        </a>
        <nav className="nav-links">
          <a href="#ask">Try it</a>
          <a href="#how">How it works</a>
        </nav>
        <div className="nav-right">
          <ServerStatus state={server} />
          <a className="btn btn-dark btn-sm" href={SOURCE} target="_blank" rel="noreferrer">
            <Github size={16} aria-hidden="true" />
            <span className="hide-sm">Source</span>
          </a>
        </div>
      </div>
    </header>
  );
}

function ServerStatus({ state }) {
  const text = {
    checking: 'Connecting…',
    up: 'Server ready',
    waking: 'Waking up the server…',
    down: 'Server not reachable',
  }[state];
  return (
    <span
      className={`status status-${state}`}
      role="status"
      title={state === 'waking' ? 'Free hosting sleeps when idle; this takes about a minute.' : undefined}
    >
      <span className="dot" aria-hidden="true" />
      {text}
    </span>
  );
}
