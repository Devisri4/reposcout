// Where the backend lives. Empty means "same address as this page" (Vite forwards /api in development).
// On Vercel, set VITE_API_URL to the Render backend, e.g. https://reposcout-api.onrender.com
const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');

/** Asks the agent a question. Resolves to {repo, branch, answer, citations, steps, toolCalls}. */
export async function askRepo(repoUrl, question) {
  let res;
  try {
    res = await fetch(`${BASE}/api/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ repoUrl, question }),
    });
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
