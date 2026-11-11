import { useEffect, useRef, useState } from 'react';
import { FolderGit2, Plus, SendHorizontal } from 'lucide-react';
import { askRepo, isServerUp } from './api.js';
import Navbar from './components/Navbar.jsx';
import Hero from './components/Hero.jsx';
import HowItWorks from './components/HowItWorks.jsx';
import AnswerCard from './components/AnswerCard.jsx';

const EXAMPLE_REPO = 'https://github.com/spring-projects/spring-petclinic';
const EXAMPLE_QUESTIONS = [
  'How is a new owner validated and saved?',
  'Where is the database configured?',
  'How are vets loaded and cached?',
];
const MAX_QUESTION = 500;

export default function App() {
  const [repoUrl, setRepoUrl] = useState(EXAMPLE_REPO);
  const [question, setQuestion] = useState('');
  const [entries, setEntries] = useState([]);
  const [server, setServer] = useState('checking'); // checking | up | waking | down
  const nextId = useRef(1);
  const bottomRef = useRef(null);
  const askRef = useRef(null);
  const inputRef = useRef(null);

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
    if (entries.length > 0) bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
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

  function tryExample() {
    setRepoUrl(EXAMPLE_REPO);
    askRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setTimeout(() => inputRef.current?.focus(), 400);
  }

  function onKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <>
      <Navbar server={server} />
      <Hero onTry={tryExample} />

      <main className="section" id="ask" ref={askRef}>
        <div className="section-head row">
          <div>
            <h2>Ask the agent</h2>
            <p>Answers usually take 20–60 seconds on the free AI tier. Each visitor can ask 10 questions an hour.</p>
          </div>
          {entries.length > 0 && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEntries([])} disabled={busy}>
              <Plus size={16} aria-hidden="true" />
              New chat
            </button>
          )}
        </div>

        <div className="chat">
          <div className="repo-bar">
            <FolderGit2 size={18} aria-hidden="true" />
            <label htmlFor="repo" className="sr-only">
              Repository
            </label>
            <input
              id="repo"
              type="text"
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              placeholder="https://github.com/owner/repo"
              autoComplete="off"
              spellCheck="false"
            />
          </div>

          <div className="messages">
            {entries.length === 0 && (
              <div className="empty">
                <p className="empty-title">Ask anything about the repository above</p>
                <p>
                  The agent downloads it once, then searches and reads files until it can answer. Try a question below,
                  or paste another public repo.
                </p>
              </div>
            )}
            {entries.map((entry) => (
              <AnswerCard key={entry.id} entry={entry} />
            ))}
            <div ref={bottomRef} />
          </div>

          {repoUrl.trim() === EXAMPLE_REPO && (
            <div className="chips">
              {EXAMPLE_QUESTIONS.map((q) => (
                <button key={q} type="button" className="chip" disabled={busy} onClick={() => submit(q)}>
                  {q}
                </button>
              ))}
            </div>
          )}

          <form
            className="composer"
            onSubmit={(e) => {
              e.preventDefault();
              submit();
            }}
          >
            <label htmlFor="question" className="sr-only">
              Question
            </label>
            <textarea
              id="question"
              ref={inputRef}
              rows={1}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder="Ask about the code, e.g. How does login work?"
              maxLength={MAX_QUESTION}
            />
            <span className="counter" aria-hidden="true">
              {MAX_QUESTION - question.length}
            </span>
            <button
              type="submit"
              className="send"
              aria-label="Ask"
              disabled={busy || !question.trim() || !repoUrl.trim()}
            >
              <SendHorizontal size={18} aria-hidden="true" />
            </button>
          </form>
        </div>
      </main>

      <HowItWorks />

      <footer className="footer">
        <span>
          Built by <a href="https://github.com/Devisri4" target="_blank" rel="noreferrer">Devi</a> with Java 21, Spring
          Boot, Spring AI and React.
        </span>
        <a href="https://github.com/Devisri4/reposcout" target="_blank" rel="noreferrer">
          Source on GitHub
        </a>
      </footer>
    </>
  );
}
