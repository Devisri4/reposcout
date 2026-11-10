/** Every tool call the agent made, in order. */
export default function StepsPanel({ steps = [] }) {
  return (
    <details className="panel">
      <summary>
        Agent steps <span className="badge">{steps.length}</span>
      </summary>
      {steps.length === 0 && <p className="muted small">The agent answered without using any tools.</p>}
      <ol className="steps">
        {steps.map((s) => (
          <li key={s.number}>
            <code className="tool">{s.tool}</code>
            <span className="input">{s.input}</span>
            <span className={s.summary.startsWith('Error') || s.summary.startsWith('refused') ? 'summary bad' : 'summary'}>
              → {s.summary}
            </span>
            <span className="muted small"> {s.millis} ms</span>
          </li>
        ))}
      </ol>
    </details>
  );
}
