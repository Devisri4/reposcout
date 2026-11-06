# RepoScout

An AI agent that explores a public GitHub repository and answers questions about the code, with links to
the exact files it used — and every file reference checked before it is shown.

> Work in progress. Phase 1 (repo explorer) is being built.

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.5 |
| AI | Spring AI with tool calling, Groq free tier (planned) |
| Frontend | React (planned) |

## Run the backend locally

```bash
cd backend
./mvnw spring-boot:run
```

Health check: http://localhost:8080/actuator/health
