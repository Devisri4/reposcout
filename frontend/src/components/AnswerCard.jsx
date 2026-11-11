import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Bot } from 'lucide-react';
import CitationList from './CitationList.jsx';
import StepsPanel from './StepsPanel.jsx';

/** One question (user bubble) and the agent's reply (loading, error or answer). */
export default function AnswerCard({ entry }) {
  const seconds = useElapsedSeconds(entry);
  const { result } = entry;

  return (
    <div className="turn">
      <div className="bubble-user">
        <p>{entry.question}</p>
        <small>{shortRepo(entry.repoUrl)}</small>
      </div>

      <div className="reply">
        <span className="avatar" aria-hidden="true">
          <Bot size={18} />
        </span>
        <div className="bubble-agent">
          {entry.status === 'loading' && (
            <div className="loading" role="status">
              <span className="spinner" aria-hidden="true" />
              Exploring the repository… {seconds}s
            </div>
          )}

          {entry.status === 'error' && <div className="error">{entry.error}</div>}

          {entry.status === 'done' && result && (
            <>
              <div className="answer markdown">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm]}
                  components={{
                    // Open GitHub links in a new tab. `node` is dropped so it isn't passed to the DOM.
                    a: ({ node, ...props }) => <a {...props} target="_blank" rel="noreferrer" />,
                  }}
                >
                  {result.answer}
                </ReactMarkdown>
              </div>
              <p className="meta">
                {result.repo} · branch {result.branch} · {result.toolCalls} tool calls · {seconds}s
              </p>
              <div className="panels">
                <CitationList citations={result.citations} />
                <StepsPanel steps={result.steps} />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function useElapsedSeconds(entry) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (entry.status !== 'loading') return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [entry.status]);
  const end = entry.finishedAt ?? now;
  return Math.max(0, Math.round((end - entry.startedAt) / 1000));
}

function shortRepo(url) {
  return url.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\.git$/, '');
}
