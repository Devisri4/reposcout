import { CircleCheck, CircleX } from 'lucide-react';

/** The file references from the answer: verified ones link to GitHub, removed ones show why. */
export default function CitationList({ citations = [] }) {
  const verified = citations.filter((c) => c.verified);
  const removed = citations.filter((c) => !c.verified);

  return (
    <details className="panel" open>
      <summary>
        Files used <span className="badge ok">{verified.length} verified</span>
        {removed.length > 0 && <span className="badge bad">{removed.length} removed</span>}
      </summary>
      {citations.length === 0 && <p className="muted small">The answer did not cite any files.</p>}
      <ul className="citations">
        {verified.map((c) => (
          <li key={key(c)}>
            <CircleCheck size={16} className="tick" aria-label="verified" />
            <a href={c.url} target="_blank" rel="noreferrer">
              <code>{label(c)}</code>
            </a>
          </li>
        ))}
        {removed.map((c) => (
          <li key={key(c)} className="removed">
            <CircleX size={16} className="cross" aria-label="removed" />
            <span className="cite-body">
              <code>{label(c)}</code>
              <span className="muted small"> — {c.reason}</span>
            </span>
          </li>
        ))}
      </ul>
    </details>
  );
}

function label(c) {
  return c.startLine === c.endLine ? `${c.path}:${c.startLine}` : `${c.path}:${c.startLine}-${c.endLine}`;
}

function key(c) {
  return `${c.path}:${c.startLine}-${c.endLine}`;
}
