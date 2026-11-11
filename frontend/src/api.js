// Where the backend lives. Empty means "same address as this page" (Vite forwards /api in development).
// On Vercel, set VITE_API_URL to the Render backend, e.g. https://reposcout-api.onrender.com
const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');

/** Calls the API and returns its JSON, or throws an Error with the server's message. */
async function request(path, options) {
  let res;
  try {
    res = await fetch(`${BASE}${path}`, options);
  } catch {
    throw new Error('Could not reach the server. Check your connection and try again.');
  }
  let data = null;
  try {
    data = await res.json();
  } catch {
    // Not JSON (e.g. a proxy error page); handled below.
  }
  if (!res.ok) {
    throw new Error(data?.error || `The server returned an error (${res.status}).`);
  }
  return data;
}

function postJson(path, body) {
  return request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

/** Asks the agent a question. Resolves to {repo, branch, answer, citations, steps, toolCalls}. */
export function askRepo(repoUrl, question) {
  return postJson('/api/ask', { repoUrl, question });
}

/** Matches a job description against a user's public repos. Resolves to the match result. */
export function matchJob(githubUser, jobDescription) {
  return postJson('/api/match', { githubUser, jobDescription });
}

/** Loads a saved job match by id (share links). */
export function getMatch(id) {
  return request(`/api/matches/${encodeURIComponent(id)}`);
}

/** True if the backend answers its health check within the timeout. */
export async function isServerUp(timeoutMs = 8000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${BASE}/actuator/health`, { signal: controller.signal });
    return res.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}
