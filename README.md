# RepoScout — AI agent that explains any GitHub repo, with verified file links

[![CI](https://github.com/Devisri4/reposcout/actions/workflows/ci.yml/badge.svg)](https://github.com/Devisri4/reposcout/actions/workflows/ci.yml)

Two AI tools built on one idea — **never show a file reference that hasn't been checked in plain Java**:

1. **Repo explorer** — paste a public GitHub repository, ask a question about the code, and an AI agent explores
   the repo step by step (searching, listing and reading files), then answers with links to the exact lines it used.
2. **Job match** — paste a job description and a GitHub username. RepoScout finds real code for each requirement
   in that person's public repos, writes resume bullets that link to it, **drops every claim it cannot prove**,
   and lists the skill gaps.

**Live demo: https://reposcout-nine.vercel.app**
<sub>(free hosting: the first question after a quiet period can take about a minute while the server wakes up)</sub>

![RepoScout home page](docs/screenshots/hero.png)

## Features

### Job match (Phase 2)

- **Requirements from any job post** — one AI call turns the description into up to 10 technical requirements,
  each with search terms that would appear in real code (`@SpringBootApplication`, `useState`, `FROM` …).
- **Evidence from real code** — plain Java loads the user's 6 most recently pushed public repos and searches every
  file once. Build files (`pom.xml`, `package.json`, `Dockerfile`) count most, then source code, tests and docs;
  lock files never count. The best snippets become evidence `E1`, `E2`, … with GitHub links to the exact lines.
- **Bullets that cannot lie** — a second AI call writes resume bullets that may only point to evidence ids. Java
  removes unknown ids and drops any bullet left without evidence, listing it with the reason.
- **Checklist and gaps** — each requirement is shown as proven, code found but no bullet, or a gap.
- **Shareable results** — every match is saved in Postgres and gets a link; copy one bullet or all of them.

![Job match: requirement checklist, proven bullets with file links, and skill gaps](docs/screenshots/match.png)

### Repo explorer (Phase 1)

- **Agentic repo exploration** — the model decides which tool to call next (`listFiles`, `searchCode`,
  `readFile`), up to 15 calls per question, and follows the code until it can answer.
- **Verified citations** — answers cite code as `[path/File.java:12-30]`. Each citation is checked: the file must
  exist, the lines must be inside it, and the agent must actually have read those lines. Verified ones become
  GitHub links to the exact lines; the rest are removed from the answer and listed with the reason.
- **Transparent steps** — the UI shows every tool call the agent made, its input, result and timing.
- **Any public repo** — downloaded once as a zip, unpacked safely and cached; later questions are instant to load.
- **Built for free tiers** — small file reads, a tool budget and patient retries keep it inside Groq's free
  8,000 tokens/minute; a per-visitor limit protects the quota on the public demo.

| Answer with verified file links and the agent's steps | On a phone |
|---|---|
| ![RepoScout answering a question about spring-petclinic](docs/screenshots/answer.png) | <img src="docs/screenshots/mobile.png" alt="RepoScout on a phone" width="260"> |

## How it works

```
React page (Vercel)  ── POST /api/ask ──►  Spring Boot API (Render, Docker)
                                               │
                                     RateLimiter (per visitor + per server)
                                               │
                                     RepoLoader ── GitHub API (size, branch) + zip download, cached
                                               │
                                     RepoAgent (Spring AI tool calling, Groq gpt-oss-120b)
                                       │  loop: model picks a tool ─► Java runs it ─► result goes back
                                       ▼
                             listFiles · searchCode · readFile   (sandboxed to the repo folder)
                                               │
                                     CitationChecker (plain Java) ─► verified answer + step log
```

Job match (`POST /api/match`, two AI calls per job so it fits the free tier):

```
job description ─► RequirementExtractor (AI, structured output) ─► requirements + search terms
GitHub username ─► EvidenceFinder (plain Java): list public repos, load 6 with the Phase 1 loader,
                   search all files once, rank by file type ─► evidence E1, E2 … with line links
                ─► BulletWriter (AI) ─► bullets that cite evidence ids only
                ─► MatchVerifier (plain Java) ─► keep proven bullets, drop the rest, list gaps
                ─► MatchStore (Postgres on Neon, Flyway) ─► GET /api/matches/{id} share link
```

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Web, Bean Validation, Actuator, Spring Data JPA |
| Database | PostgreSQL on Neon (free), Flyway migrations; H2 when no database is configured |
| AI | Spring AI 1.1 (`@Tool` tool calling, structured output, retries), Groq free tier via its OpenAI-compatible API, model `openai/gpt-oss-120b` |
| Frontend | React 18, Vite 5, react-markdown + GFM, lucide icons, Inter, plain CSS (light/dark, mobile-first) |
| Testing | JUnit 5, AssertJ, Mockito, MockMvc, a fake GitHub server (JDK `HttpServer`), a fake chat model, Testcontainers (real Postgres in CI); Vitest + Testing Library |
| CI/CD | GitHub Actions (backend tests, frontend tests + build, Docker image + health check), auto-deploy to Render and Vercel |
| Hosting | Render (Docker, free plan) for the API, Vercel (Hobby) for the page, Neon (free) for Postgres |

## Security design

The server downloads and reads **untrusted code from the internet** and exposes a **public AI endpoint**, so:

| Risk | Protection | Where |
|---|---|---|
| Zip slip (`../../etc/passwd` entries) | Every entry is resolved and must stay inside the target folder, or the whole unpack stops | [`ZipExtractor`](backend/src/main/java/com/devisri/reposcout/repo/ZipExtractor.java) |
| Zip bombs / huge repos | Repo size checked via the GitHub API first; download capped while streaming; total unpacked bytes and file count counted from real bytes, not zip headers; single files over 1 MB skipped | `RepoLoader`, `GitHubClient`, `ZipExtractor` |
| Path traversal through the AI | Every path the model asks for is normalised and must stay inside the repo; errors come back as text, never as file contents | [`RepoTools.resolve`](backend/src/main/java/com/devisri/reposcout/tools/RepoTools.java) |
| Hallucinated references | Citations verified against the files **and** against what the agent really read; job-match bullets may only cite evidence ids found by Java | [`CitationChecker`](backend/src/main/java/com/devisri/reposcout/citations/CitationChecker.java) |
| Runaway agent loops | Hard budget of 15 tool calls per question | [`AgentTools`](backend/src/main/java/com/devisri/reposcout/agent/AgentTools.java) |
| Quota abuse on the public demo | 10 questions per visitor per hour and 60 per server per hour; the visitor IP is taken from the proxy-added header, not the spoofable one | [`RateLimiter`](backend/src/main/java/com/devisri/reposcout/api/RateLimiter.java) |
| Other sites calling the API | CORS allows only the frontend's domain | [`CorsConfig`](backend/src/main/java/com/devisri/reposcout/api/CorsConfig.java) |
| Leaked keys | Keys only from environment variables (or a git-ignored `.env`); the GitHub token is read-only for public repos | `application.properties`, `.gitignore` |
| Container | Runs as a non-root user, JRE-only image, memory tuned for 512 MB | [`Dockerfile`](backend/Dockerfile) |

## Testing

**141 backend tests** (one of them on a real Postgres in CI) and **11 frontend tests** run on every push. Highlights:

- **Repo loader** — real HTTP against a fake GitHub server: download, cache reuse, private/missing repos,
  size limit, runaway download; zip slip, total-size and file-count limits.
- **Tools** — path escapes (`../`, `/../`, `src/../../`), binary files, 120-line read cap, search cap,
  `node_modules` skipped.
- **Citation checker** — missing files, lines past the end, lines never read, invalid ranges, every bracket and dash style
  the model uses, code snippets left untouched.
- **Agent** — tool budget, step log, the tool parameter names the model expects, Groq error handling (rate limits and the two known model glitches are
  retried; bad keys or unknown models fail at once).
- **Job match** — requirement cleanup, evidence ranking and spreading across repos, README fallback for project
  descriptions, made-up evidence ids removed, unproven bullets dropped, gaps, code with braces reaching the model
  untouched, the second AI call skipped when nothing was found, Neon connection strings, save and share round trip.
- **API** — validation, error codes, visitor limit, CORS allowed/blocked, share links.
- **Frontend** — the page asks the API and renders the answer, file links, steps and server errors; the job match
  form, result checklist, bullets, dropped claims, gaps and share links.

CI also builds the Docker image and starts it with a **512 MB memory limit** (like Render's free plan) and waits
for the health check.

```bash
cd backend && ./mvnw verify      # backend tests
cd frontend && npm test          # frontend tests
```

## Project structure

```
backend/                      Spring Boot API (Java 21)
  src/main/java/com/devisri/reposcout/
    repo/                     parse link, GitHub API, safe zip download + unpack, cache
    tools/                    listFiles, readFile, searchCode (plain Java, sandboxed)
    agent/                    Spring AI agent, tool budget, step log, Groq error handling
    match/                    job match: requirements, evidence finder, bullet writer, verifier
    store/                    Postgres/H2 storage of job matches, Neon URL support
    citations/                citation checker + read-range tracking
    api/                      REST controllers, errors, CORS, rate limiter
  src/main/resources/db/migration/   Flyway SQL
  Dockerfile                  image Render runs
frontend/                     React + Vite page
  src/components/             hero, chat, answer card, files list, steps panel, job match + results
render.yaml                   Render blueprint (backend)
.devcontainer/                GitHub Codespaces setup (Java 21 + Node 22)
docs/PLAN.md                  build plan and decisions
```

## Run it yourself

**Backend** (Java 21) — needs a free Groq key from https://console.groq.com/keys:

```bash
cd backend
cp .env.example .env          # put your key after GROQ_API_KEY=
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/ask -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/spring-projects/spring-petclinic","question":"How is a new owner validated and saved?"}'
```

**Frontend** (Node 20+), in a second terminal:

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173, calls the backend on :8080
```

Or open the repo in **GitHub Codespaces** — the dev container has Java 21 and Node ready.

| Variable | Needed | Purpose |
|---|---|---|
| `GROQ_API_KEY` | yes | Groq API key (free) |
| `GITHUB_TOKEN` | recommended in production | Read-only token; raises GitHub's API limit from 60 to 5,000 calls/hour |
| `FRONTEND_ORIGIN` | production | Page address allowed to call the API, e.g. `https://reposcout-nine.vercel.app` |
| `VITE_API_URL` | frontend, production | Backend address, e.g. `https://reposcout-api.onrender.com` |
| `DATABASE_URL` | production | Postgres connection string as Neon shows it (`postgresql://user:pass@host/db?sslmode=require`); H2 in memory if unset |
| `GROQ_MODEL` | no | Model id, default `openai/gpt-oss-120b` |

## Design decisions

| Decision | Why |
|---|---|
| Download the repo zip once and search it locally | GitHub code search allows about 10 requests/minute; one zip gives unlimited fast local search |
| Verify citations in Java instead of trusting the model | Models invent paths and line numbers; a cheap deterministic check catches it every time |
| Small reads (120 lines) and a 15-call budget | Groq's free tier is 8,000 tokens/minute for every model |
| Retry 429s and two specific model glitches, fail fast on everything else | Turns free-tier hiccups into a short wait without hiding real errors |
| Temperature 1.0 | Recommended for gpt-oss; at low temperature a tool-calling glitch repeated identically on every retry |
| Job match: evidence found by Java, bullets may only cite evidence ids | Two AI calls per job instead of dozens, and the AI cannot invent a file or line |
| No login, just a GitHub username | Only public repos are read; OAuth plus cross-site cookies would add setup without adding evidence |
| Neon Postgres, H2 fallback | Neon's free plan does not expire; H2 means local runs and tests need no setup |

## Roadmap

- **Phase 1 — Repo explorer** (done): ask questions about any public repo, get verified file links.
- **Phase 2 — Job match** (done): paste a job description; RepoScout finds evidence for each requirement in your
  repos, drafts resume bullets that cite real files, drops claims it cannot prove, and lists skill gaps.
- **Known limits**: some repos make the model "think out loud" instead of calling a tool, which fails that
  question (asking again often works); job match reads public repos only and at most 6 of them.
