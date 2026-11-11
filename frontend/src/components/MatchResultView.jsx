import { useState } from 'react';
import { Check, CircleCheck, CircleDot, CircleX, Copy, Link } from 'lucide-react';

const STATUS = {
  PROVEN: { icon: CircleCheck, cls: 'ok', label: 'Proven' },
  EVIDENCE_ONLY: { icon: CircleDot, cls: 'warn', label: 'Code found, no bullet' },
  GAP: { icon: CircleX, cls: 'bad', label: 'Gap' },
};

/** Shows a job-match result: score, requirement checklist, proven bullets, dropped claims and gaps. */
export default function MatchResultView({ result }) {
  const proven = result.requirements.filter((r) => r.status === 'PROVEN').length;
  const total = result.requirements.length;
  const pct = total ? Math.round((proven / total) * 100) : 0;

  return (
    <div className="match-result">
      <header className="match-head">
        <div>
          <h3>{result.jobTitle}</h3>
          <p className="muted">
            {result.githubUser} · {result.reposScanned.length} repos searched
            {result.reposSkipped.length > 0 && ` · ${result.reposSkipped.length} skipped`}
          </p>
        </div>
        <div className="match-actions">
          {result.bullets.length > 0 && (
            <CopyButton text={result.bullets.map((b) => `• ${b.text}`).join('\n')} label="Copy all bullets" />
          )}
          {result.saved && (
            <CopyButton text={shareUrl(result.id)} label="Copy share link" icon={Link} />
          )}
        </div>
      </header>

      <div className="score" aria-label={`${proven} of ${total} requirements proven`}>
        <div className="score-text">
          <strong>{proven}</strong> of {total} requirements proven by real code
        </div>
        <div className="score-bar">
          <span style={{ width: `${pct}%` }} />
        </div>
      </div>

      <div className="match-grid">
        <div className="match-card">
          <h4>Requirements</h4>
          <ul className="req-list">
            {result.requirements.map((r) => {
              const s = STATUS[r.status] ?? STATUS.GAP;
              const Icon = s.icon;
              return (
                <li key={r.name}>
                  <Icon size={18} className={s.cls} aria-hidden="true" />
                  <span className="req-name">{r.name}</span>
                  <span className={`req-tag ${s.cls}`}>{s.label}</span>
                </li>
              );
            })}
          </ul>
          <div className="repos">
            {result.reposScanned.map((repo) => (
              <a key={repo} href={`https://github.com/${repo}`} target="_blank" rel="noreferrer" className="repo-chip">
                {repo.split('/')[1]}
              </a>
            ))}
          </div>
        </div>

        <div className="match-card wide">
          <h4>Resume bullets</h4>
          {result.bullets.length === 0 && (
            <p className="muted">No bullet could be proven with code from these repos.</p>
          )}
          <ol className="bullets">
            {result.bullets.map((b) => (
              <li key={b.text}>
                <div className="bullet-top">
                  <p>{b.text}</p>
                  <CopyButton text={b.text} label="Copy" small />
                </div>
                <div className="evidence">
                  <span className="req-pill">{b.requirement}</span>
                  {b.evidence.map((e) => (
                    <a
                      key={e.id}
                      href={e.url}
                      target="_blank"
                      rel="noreferrer"
                      className="ev-chip"
                      title={e.excerpt}
                    >
                      {e.repo.split('/')[1]} · {e.path.split('/').pop()}:{e.startLine}-{e.endLine}
                    </a>
                  ))}
                </div>
              </li>
            ))}
          </ol>

          {result.dropped.length > 0 && (
            <details className="panel dropped">
              <summary>
                Dropped claims <span className="badge bad">{result.dropped.length}</span>
              </summary>
              <ul>
                {result.dropped.map((d) => (
                  <li key={d.text}>
                    <s>{d.text}</s> <span className="muted small">— {d.reason}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      </div>

      {result.gaps.length > 0 && (
        <div className="gaps">
          <h4>Skill gaps</h4>
          <p className="muted">No code was found for these. A small project that uses them would close the gap.</p>
          <div className="gap-chips">
            {result.gaps.map((g) => (
              <span key={g} className="gap-chip">
                {g}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function CopyButton({ text, label, icon: Icon = Copy, small = false }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard blocked (e.g. not https); nothing else to do.
    }
  }
  return (
    <button type="button" className={`btn btn-ghost ${small ? 'btn-xs' : 'btn-sm'}`} onClick={copy}>
      {copied ? <Check size={14} aria-hidden="true" /> : <Icon size={14} aria-hidden="true" />}
      {copied ? 'Copied' : label}
    </button>
  );
}

function shareUrl(id) {
  return `${window.location.origin}${window.location.pathname}#match/${id}`;
}
