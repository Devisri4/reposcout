# RepoScout — Plan

RepoScout is an AI agent that explores a public GitHub repository and answers questions about the code,
with links to the exact files it used. Every file reference is checked before it is shown.

## Phases

| Phase | What it does |
|---|---|
| **1. Repo explorer** | Paste a public repo link, ask a question ("How does login work?"). The agent opens files, follows the code and answers with verified file links. The UI shows each step the agent took. |
| **2. Job match** | Paste a job description. The agent searches the user's repos for evidence of each requirement, drafts resume bullets that cite real files, drops claims it cannot prove, and lists skill gaps. |

## Architecture (Phase 1)

```
React page  ->  Spring Boot API  ->  Agent (Spring AI + Groq)
                                        | decides which tool to call next
                                        v
                               Tools working on the repo:
                               - listFiles(folder)
                               - readFile(path, lines)
                               - searchCode(word)
                                        |
                               Repo downloaded once as a zip,
                               unpacked on the server and reused
```

## Key decisions

| Decision | Why |
|---|---|
| Download the repo zip once and search it on the server | GitHub code search allows ~10 searches/minute. One zip download, then unlimited fast local search. |
| No database in Phase 1 | Not needed yet. Postgres arrives in Phase 2. |
| Max ~15 tool calls per question | Stays inside Groq's free limits and stops runaway loops. |
| Model `openai/gpt-oss-120b` on Groq, low reasoning effort, retry on 429 | Free tier is 8000 tokens/minute for every model; small reads + patient retries keep a question at ~20-60 s. |
| Read files in small line ranges | Large files would burn the free token budget. |
| Citation check in plain Java after the answer | The AI's file references are verified, not trusted. |
| Public repos only, with a size limit | Safe, simple, no login needed. |
| Free tools only | Groq free tier, GitHub API, Render + Vercel free hosting. |

## Phase 1 build steps

| # | Step | Done when | Status |
|---|---|---|---|
| 1 | Repo, Spring Boot skeleton, CI (React skeleton moved to step 6) | Build + health test pass | Done |
| 2 | Repo loader: download zip, unpack safely, size limit | Unit tests pass | Done |
| 3 | Tools: listFiles, readFile, searchCode (no AI) | Unit tests pass | Done |
| 4 | Spring AI + Groq: agent answers using the tools | Real answers via HTTP | Done |
| 5 | Citation checker + step log | Answers show verified files | Done |
| 6 | React UI: repo input, chat, file links, steps panel (needs Node.js) | Works locally | Done (CI build green; Codespaces blocked on office network, confirmed on the live site) |
| 7 | Deploy (Render + Vercel), README, screenshots | Live demo link | Done: https://reposcout-nine.vercel.app |

Phase 1 is done when any public repo can be pasted, a question asked, and a correct answer with verified
file links comes back — live on the internet.
