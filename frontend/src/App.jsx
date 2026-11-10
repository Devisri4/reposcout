import { useEffect, useRef, useState } from 'react';
import { askRepo, isServerUp } from './api.js';
import AnswerCard from './components/AnswerCard.jsx';

const EXAMPLE_REPO = 'https://github.com/spring-projects/spring-petclinic';
const EXAMPLE_QUESTIONS = [
  'How is a new owner validated and saved?',
  'Where is the database configured?',
  'How are vets loaded and cached?',
];

export default function App() {
  const [repoUrl, setRepoUrl] = useState(EXAMPLE_REPO);
  const [question, setQuestion] = useState('');
  const [entries, setEntries] = useState([]);
  const [server, setServer] = useState('checking'); // checking | up | waking | down
  const nextId = useRef(1);
  const bottomRef = useRef(null);

  // Free hosting sleeps when idle; ping the health check until the server is awake.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      for (let attempt = 0; attempt < 20 && !cancelled; attempt++) {
        if (await isServerUp()) {
          if (!cancelled) setServer('up');
          return;
        }
        if (!cancelled) setServer('waking');
        await new Promise((r) => setTimeout(r, 5000));
      }
      if (!cancelled) setServer('down');
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [entries.length]);

  const busy = entries.some((e) => e.status === 'loading');

  async function submit(text) {
    const q = (text ?? question).trim();
    if (!q || !repoUrl.trim() || busy) return;
    const id = nextId.current++;
    setEntries((list) => [...list, { id, repoUrl: repoUrl.trim(), question: q, status: 'loading', startedAt: Date.now() }]);
    setQuestion('');
    try {
      const result = await askRepo(repoUrl.trim(), q);
      setEntries((list) => list.map((e) => (e.id === id ? { ...e, status: 'done', result, finishedAt: Date.now() } : e)));
      setServer('up');
    } catch (err) {
      setEntries((list) => list.map((e) => (e.id === id ? { ...e, status: 'error', error: err.message, finishedAt: Date.now() } : e)));
    }
  }

  function onKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <div className="page">
      <header className="header">
        <div>
          <h1>RepoScout</h1>
          <p className="tagline">Ask any public GitHub repository a question. Every file link in the answer is checked before it is shown.</p>
        </div>
        <ServerStatus state={server} />
      </header>

      <main>
        <form
          className="card ask-form"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <label htmlFor="repo">Repository</label>
          <input
            id="repo"
            type="text"
            value={repoUrl}
            onChange={(e) => setRepoUrl(e.target.value)}
            placeholder="https://github.com/owner/repo"
            autoComplete="off"
            spellCheck="false"
          />

          <label htmlFor="question">Question</label>
          <textarea
            id="question"
            rows={3}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="How does login work?"
            maxLength={500}
          />

          <div className="form-row">
            <div className="examples">
              {repoUrl.trim() === EXAMPLE_REPO &&
                EXAMPLE_QUESTIONS.map((q) => (
                  <button key={q} type="button" className="chip" disabled={busy} onClick={() => submit(q)}>
                    {q}
                  </button>
                ))}
            </div>
            <button type="submit" className="primary" disabled={busy || !question.trim() || !repoUrl.trim()}>
              {busy ? 'Exploring…' : 'Ask'}
            </button>
          </div>
        </form>

        {entries.length === 0 && (
          <p className="empty">
            The agent downloads the repo once, then searches and reads files step by step. Answers usually take 20–60 seconds on the free AI tier.
          </p>
        )}

        {entries.map((entry) => (
          <AnswerCard key={entry.id} entry={entry} />
        ))}
        <div ref={bottomRef} />
      </main>

      <footer className="footer">
        Built with Java 21, Spring Boot, Spring AI and React ·{' '}
        <a href="https://github.com/Devisri4/reposcout" target="_blank" rel="noreferrer">
          Source on GitHub
        </a>
      </footer>
    </div>
  );
}

function ServerStatus({ state }) {
  const text = {
    checking: 'Connecting…',
    up: 'Server ready',
    waking: 'Waking up the server (free hosting sleeps when idle)…',
    down: 'Server not reachable',
  }[state];
  return (
    <span className={`status status-${state}`} role="status">
      <span className="dot" aria-hidden="true" />
      {text}
    </span>
  );
}
