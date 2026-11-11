import { useEffect, useRef, useState } from 'react';
import { Briefcase, Github, Sparkles } from 'lucide-react';
import { getMatch, matchJob } from '../api.js';
import MatchResultView from './MatchResultView.jsx';

const MAX_JOB = 8000;
const EXAMPLE_JOB = `Java Full Stack Developer

We are looking for a developer to build and maintain web applications.

Requirements:
- Strong Java and Spring Boot for REST APIs
- React and JavaScript for front ends
- SQL databases such as PostgreSQL or MySQL
- Unit testing with JUnit and Mockito
- Docker and CI/CD with GitHub Actions
- Cloud deployment experience
- Good communication skills and teamwork`;

const STAGES = ['Reading the job description…', 'Searching your public repos…', 'Writing bullets and checking every one…'];

/** Phase 2: paste a job description, get resume bullets proven by the user's own repos. */
export default function JobMatch() {
  const [user, setUser] = useState('');
  const [job, setJob] = useState('');
  const [state, setState] = useState({ status: 'idle' }); // idle | loading | done | error
  const [seconds, setSeconds] = useState(0);
  const sectionRef = useRef(null);

  // Share links: #match/<id> opens a saved result.
  useEffect(() => {
    const id = sharedId();
    if (!id) return;
    setState({ status: 'loading', shared: true });
    getMatch(id)
      .then((result) => {
        setState({ status: 'done', result });
        sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      })
      .catch((err) => setState({ status: 'error', error: err.message }));
  }, []);

  useEffect(() => {
    if (state.status !== 'loading') return undefined;
    const started = Date.now();
    setSeconds(0);
    const timer = setInterval(() => setSeconds(Math.round((Date.now() - started) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [state.status]);

  const busy = state.status === 'loading';
  const canSubmit = user.trim() && job.trim().length >= 40 && !busy;

  async function submit(event) {
    event.preventDefault();
    if (!canSubmit) return;
    setState({ status: 'loading' });
    try {
      const result = await matchJob(user.trim(), job.trim());
      setState({ status: 'done', result });
      if (result.saved) {
        window.history.replaceState(null, '', `#match/${result.id}`);
      }
    } catch (err) {
      setState({ status: 'error', error: err.message });
    }
  }

  const stage = STAGES[Math.min(STAGES.length - 1, Math.floor(seconds / 8))];

  return (
    <section className="section" id="match" ref={sectionRef}>
      <div className="section-head">
        <span className="kicker">
          <Sparkles size={14} aria-hidden="true" /> New · Phase 2
        </span>
        <h2>Job match</h2>
        <p>
          Paste a job description and a GitHub username. RepoScout finds real code for each requirement in that
          person's public repos, writes resume bullets that link to it, drops anything it cannot prove, and lists the
          gaps.
        </p>
      </div>

      <form className="match-form" onSubmit={submit}>
        <div className="field">
          <label htmlFor="gh-user">GitHub username</label>
          <div className="input-icon">
            <Github size={18} aria-hidden="true" />
            <input
              id="gh-user"
              type="text"
              value={user}
              onChange={(e) => setUser(e.target.value)}
              placeholder="e.g. Devisri4"
              autoComplete="off"
              spellCheck="false"
            />
          </div>
        </div>

        <div className="field">
          <div className="field-row">
            <label htmlFor="job">Job description</label>
            <button type="button" className="link-btn" onClick={() => setJob(EXAMPLE_JOB)} disabled={busy}>
              Use an example job
            </button>
          </div>
          <textarea
            id="job"
            rows={8}
            value={job}
            onChange={(e) => setJob(e.target.value)}
            placeholder="Paste the job post here (at least a few lines)…"
            maxLength={MAX_JOB}
          />
          <small className="muted">{job.length} / {MAX_JOB}</small>
        </div>

        <button type="submit" className="btn btn-primary" disabled={!canSubmit}>
          <Briefcase size={18} aria-hidden="true" />
          {busy && !state.shared ? 'Matching…' : 'Match my repos'}
        </button>
      </form>

      <div className="match-output" aria-live="polite">
        {busy && (
          <div className="match-loading" role="status">
            <span className="spinner" aria-hidden="true" />
            {state.shared ? 'Opening the shared result…' : `${stage} ${seconds}s`}
          </div>
        )}
        {state.status === 'error' && <div className="error">{state.error}</div>}
        {state.status === 'done' && <MatchResultView result={state.result} />}
      </div>
    </section>
  );
}

function sharedId() {
  const m = window.location.hash.match(/^#match\/([0-9a-f-]{36})$/i);
  return m ? m[1] : null;
}
