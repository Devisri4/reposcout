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

## Architecture (Phase 2: job match)

```
Job description + GitHub username
        |
        v
1. RequirementExtractor (1 AI call)   -> up to 10 requirements, each with code search terms
2. EvidenceFinder (plain Java)        -> loads the user's public repos (Phase 1 loader), searches them,
                                         keeps the best code snippets per requirement as evidence E1, E2...
3. BulletWriter (1 AI call)           -> resume bullets that may only point to evidence ids
4. MatchVerifier (plain Java)         -> drops bullets with no valid evidence, lists skill gaps
5. Saved in Postgres                  -> shareable result link
```

## Key decisions (Phase 2)

| Decision | Why |
|---|---|
| GitHub username instead of login | Public repos need no login; GitHub OAuth plus cross-site cookies between Vercel and Render would add a lot of setup for no extra evidence. |
| Evidence is found by plain Java search, not by the AI | Two AI calls per job instead of dozens of tool calls: fits Groq's 8,000 tokens/minute, and every snippet is real code. |
| Bullets may only cite evidence ids (E1, E2...) | The AI cannot invent a file or line; unknown ids are dropped in Java, and bullets left with none are removed. |
| Postgres on Neon (free), H2 when no database is configured | Neon's free plan does not expire; H2 keeps local runs and tests working with no setup. Flyway manages the schema. |
| At most 6 repos per user (most recently pushed, not forks) | Keeps download time and disk use small on the free server. |

## Phase 2 build steps

| # | Step | Done when | Status |
|---|---|---|---|
| 8 | Requirement extractor: job description -> requirements + search terms (AI, structured output) | Unit tests pass | Done |
| 9 | Evidence finder: list a user's public repos, search them, pick snippets per requirement (no AI) | Unit tests pass | Done |
| 10 | Bullet writer + verifier: bullets citing evidence ids, drop unproven claims, skill gaps; `POST /api/match` | Real results via HTTP | Done |
| 11 | Postgres (Neon) + Flyway: save each match, `GET /api/matches/{id}` share link | Tests pass on H2 and in CI | Done (Postgres test runs in CI) |
| 12 | Job match UI: form, requirement checklist, bullets with file links, dropped claims, gaps | Works in the browser | Done |
| 13 | Deploy (Neon + Render), README, screenshots | Live job match | Next |

Phase 2 is done when a job description and a GitHub username give resume bullets that link to real files in
that user's repos, with unproven claims dropped and gaps listed — live on the internet.
