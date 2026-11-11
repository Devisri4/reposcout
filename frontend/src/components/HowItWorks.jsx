import { Download, FileSearch, ListTree, ShieldCheck } from 'lucide-react';

const STEPS = [
  {
    icon: Download,
    title: 'Downloads the repo once',
    text: 'The public repo is fetched as a zip, unpacked safely with size limits, and cached for later questions.',
  },
  {
    icon: FileSearch,
    title: 'Explores like a developer',
    text: 'The AI decides what to do next — list folders, search for names, read line ranges — up to 15 tool calls.',
  },
  {
    icon: ShieldCheck,
    title: 'Verifies every citation',
    text: 'Plain Java checks each file link: the file exists, the lines exist, and the agent really read them.',
  },
  {
    icon: ListTree,
    title: 'Shows its work',
    text: 'Every step the agent took is listed with its input and result, so you can see how it found the answer.',
  },
];

export default function HowItWorks() {
  return (
    <section className="section" id="how">
      <div className="section-head">
        <h2>How it works</h2>
        <p>Java 21, Spring Boot and Spring AI tool calling on the free Groq tier, with a React front end.</p>
      </div>
      <div className="features">
        {STEPS.map(({ icon: Icon, title, text }, i) => (
          <article className="feature" key={title}>
            <span className="feature-icon" aria-hidden="true">
              <Icon size={20} />
            </span>
            <span className="feature-step">Step {i + 1}</span>
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
