# RepoScout — notes for Claude

Personal portfolio project by Devi (GitHub: Devisri4), built to get SDE / Java / Full Stack roles.
The plan, decisions and step status live in [docs/PLAN.md](docs/PLAN.md) — read it first and update the
status column when a step is finished.

## How to work here
- One small step at a time. Explain simply; don't add work that isn't in the plan.
- Verify before saying something works (run the build/tests and show the result).
- Free tools only. Never commit keys — they come from environment variables.
- Keep this project fully separate from any employer code.

## Build
- Java 21 is at `C:\Java\jdk-21.0.12.1+1` (PATH default is Java 17), so set `JAVA_HOME` to it:
  `$env:JAVA_HOME='C:\Java\jdk-21.0.12.1+1'; cd backend; .\mvnw.cmd verify`
- Company laptop: nothing can be installed. Node.js is not available, so build the React frontend
  (step 6) in GitHub Codespaces or StackBlitz, with Vercel/CI doing the real builds.
- GitHub username: Devisri4. Devi pushes to GitHub themselves.
- Commit once per finished step (not many tiny commits); tell Devi when it's a good time to push.
