import { ArrowRight, Briefcase, CircleCheck, CircleX, ShieldCheck } from 'lucide-react';

/** Top section: what RepoScout is, with a small preview of a checked answer. */
export default function Hero({ onTry, onMatch }) {
  return (
    <section className="hero" id="top">
      <div className="hero-inner">
        <div className="hero-text">
          <span className="eyebrow">
            <ShieldCheck size={14} aria-hidden="true" />
            AI agent · every file link verified
          </span>
          <h1>
            Understand any codebase <span className="accent">in minutes.</span>
          </h1>
          <p className="lead">
            Paste a public GitHub repository and ask a question. RepoScout searches and reads the code step by step,
            then answers with links to the exact lines it used — each one checked before you see it.
          </p>
          <div className="hero-actions">
            <button type="button" className="btn btn-light" onClick={onTry}>
              Try it on spring-petclinic
              <ArrowRight size={18} aria-hidden="true" />
            </button>
            <button type="button" className="btn btn-outline" onClick={onMatch}>
              <Briefcase size={18} aria-hidden="true" />
              Match a job to your repos
            </button>
          </div>
        </div>

        <div className="hero-visual" aria-hidden="true">
          <div className="preview">
            <div className="preview-bar">
              <span />
              <span />
              <span />
              <code>spring-projects/spring-petclinic</code>
            </div>
            <p className="preview-q">Where is the owner form validated?</p>
            <p className="preview-a">
              The controller method receives the owner with <code>@Valid</code> and checks{' '}
              <code>result.hasErrors()</code> before saving it.
            </p>
            <ul className="preview-cites">
              <li>
                <CircleCheck size={16} className="ok" />
                <code>OwnerController.java:77-84</code>
              </li>
              <li>
                <CircleCheck size={16} className="ok" />
                <code>Owner.java:52-65</code>
              </li>
              <li className="removed">
                <CircleX size={16} className="bad" />
                <code>OwnerValidator.java:12</code>
                <small>never opened — removed</small>
              </li>
            </ul>
          </div>
          <div className="float-card">
            <span className="float-icon">
              <ShieldCheck size={18} />
            </span>
            <div>
              <strong>Citations checked in Java</strong>
              <small>File, lines, and that the agent read them</small>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
