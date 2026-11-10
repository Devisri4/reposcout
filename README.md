# RepoScout

[![CI](https://github.com/Devisri4/reposcout/actions/workflows/ci.yml/badge.svg)](https://github.com/Devisri4/reposcout/actions/workflows/ci.yml)

**An AI agent that explores any public GitHub repository and answers questions about the code — with links
to the exact lines it used, and every link checked before it is shown.**

**Live demo:** _coming soon_

<!-- Screenshot: add docs/screenshot.png after deploying, then uncomment:
![RepoScout answering a question about spring-petclinic](docs/screenshot.png)
-->

## What it does

1. Paste a public repo link and ask a question, e.g. *"How is a new owner validated and saved?"*
2. The agent downloads the repo once, then **searches, lists and reads files step by step** — like a developer
   new to the codebase — until it can answer.
3. The answer cites code as `[path/File.java:12-30]`. Before it is shown, plain Java code **verifies every
   citation**: the file must exist, the lines must be inside it, and the agent must actually have read them.
   Verified citations become GitHub links to those lines; anything else is removed and listed with the reason.
4. The UI shows every step the agent took (`searchCode("Owner") → 640 matches`, `readFile(...) → lines 1-120`).

## How it works

```
React (Vercel)  ──POST /api/ask──►  Spring Boot API (Render, Docker)
                                         │
                                         ▼
                              RepoAgent (Spring AI + Groq, tool calling)
                                │  decides which tool to call next (max 15 calls)
                                ▼
                     listFiles(folder) · readFile(path, lines) · searchCode(text)
                                │
                     repo downloaded once as a zip, unpacked safely, cached
                                │
                                ▼
                     CitationChecker (plain Java) ──► verified answer + steps
```

| Part | Where | Notes |
|---|---|---|
| Repo loader | [`repo/`](backend/src/main/java/com/devisri/reposcout/repo) | GitHub API for size/branch, zip download with a byte cap, zip-slip protection, file-count and size limits, per-repo cache |
| Tools | [`tools/RepoTools.java`](backend/src/main/java/com/devisri/reposcout/tools/RepoTools.java) | Every path is checked to stay inside the repo; binary files, huge lines and `node_modules` are handled |
| Agent | [`agent/`](backend/src/main/java/com/devisri/reposcout/agent) | Spring AI `@Tool` methods, per-question tool budget, step log, friendly errors for rate limits |
| Citation checker | [`citations/`](backend/src/main/java/com/devisri/reposcout/citations) | Verifies file, line range and that the lines were really read; builds GitHub line links |
| UI | [`frontend/`](frontend) | React + Vite: answer with Markdown, verified/removed file list, agent steps panel |

## Design decisions

| Decision | Why |
|---|---|
| Download the repo zip once and search locally | GitHub code search allows ~10 requests/minute; one zip gives unlimited fast local search |
| AI citations are verified in plain Java, not trusted | LLMs invent file paths and line numbers; the checker catches it every time |
| Tool budget of 15 calls, file reads of at most 120 lines | Groq's free tier is 8,000 tokens/minute; small reads keep a question around 20–60 seconds |
| Groq through Spring AI's OpenAI-compatible client | Free, fast, and switching models is one setting (`GROQ_MODEL`) |
| Retry on HTTP 429 with backoff | Free-tier rate limits become a short wait instead of an error |
| No database in Phase 1 | Not needed yet; Postgres arrives with Phase 2 |

## Tech stack

Java 21 · Spring Boot 3.5 · Spring AI 1.1 (tool calling) · Groq (`openai/gpt-oss-120b`) · React 18 + Vite ·
JUnit 5, AssertJ, MockMvc · GitHub Actions · Docker · Render + Vercel

## Tests

67 backend tests run on every push, including:
- zip-slip, size-limit and too-many-files protection
- a fake GitHub server (JDK `HttpServer`) for download, cache and error cases
- path-escape attempts (`../`, absolute paths) against every tool
- the citation checker: missing files, lines past the end, lines never read, made-up ranges
- the tool budget and the step log
- the HTTP API: validation, error codes, CORS

CI also builds the React app and the Docker image, and starts the container with a 512 MB memory limit.

## Run it yourself

**Backend** (Java 21) — needs a free Groq key from https://console.groq.com/keys:

```bash
cd backend
cp .env.example .env        # then put your key after GROQ_API_KEY=
./mvnw spring-boot:run      # Windows: .\mvnw.cmd spring-boot:run
```

Ask a question:

```bash
curl -X POST http://localhost:8080/api/ask -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/spring-projects/spring-petclinic","question":"How is a new owner validated and saved?"}'
```

**Frontend** (Node 20+), in a second terminal:

```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173 (calls the backend on :8080)
```

Or open the repo in **GitHub Codespaces** — the dev container has Java 21 and Node ready.

### Configuration

| Variable | Needed | Purpose |
|---|---|---|
| `GROQ_API_KEY` | yes | Groq API key (free) |
| `GROQ_MODEL` | no | Model id, default `openai/gpt-oss-120b` |
| `GITHUB_TOKEN` | no | Raises GitHub's API limit from 60 to 5,000 calls/hour |
| `FRONTEND_ORIGIN` | in production | Page address allowed to call the API, e.g. `https://reposcout.vercel.app` |
| `VITE_API_URL` | frontend, in production | Backend address, e.g. `https://reposcout-api.onrender.com` |

## Roadmap

- **Phase 1 — Repo explorer** (this): ask questions about any public repo, get verified file links.
- **Phase 2 — Job match**: paste a job description; the agent finds evidence for each requirement in your repos,
  drafts resume bullets that cite real files, drops claims it cannot prove, and lists skill gaps.
