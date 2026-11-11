import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import JobMatch from './JobMatch.jsx';

const ID = '3f1e2d4c-5b6a-4789-8abc-def012345678';

const RESULT = {
  id: ID,
  githubUser: 'Devisri4',
  jobTitle: 'Java Full Stack Developer',
  createdAt: '2026-11-11T10:00:00Z',
  reposScanned: ['Devisri4/shop', 'Devisri4/ui'],
  reposSkipped: [],
  requirements: [
    { name: 'Spring Boot', status: 'PROVEN', evidenceCount: 2 },
    { name: 'JUnit', status: 'EVIDENCE_ONLY', evidenceCount: 1 },
    { name: 'Kubernetes', status: 'GAP', evidenceCount: 0 },
  ],
  bullets: [
    {
      text: 'Built an online shop backend with Spring Boot and PostgreSQL.',
      requirement: 'Spring Boot',
      evidence: [{ id: 'E1', requirement: 'Spring Boot', repo: 'Devisri4/shop', path: 'backend/pom.xml', startLine: 5,
        endLine: 10, matchedTerm: 'spring-boot-starter', excerpt: '5: <artifactId>spring-boot-starter</artifactId>',
        url: 'https://github.com/Devisri4/shop/blob/main/backend/pom.xml#L5-L10' }],
    },
  ],
  dropped: [{ text: 'Deployed to Kubernetes.', reason: 'cited evidence that does not exist (E9)' }],
  gaps: ['Kubernetes'],
  saved: true,
};

function json(status, body) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('JobMatch', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/');
    vi.spyOn(globalThis, 'fetch').mockImplementation((url) =>
      String(url).includes('/api/match') ? json(200, RESULT) : json(404, { error: 'not found' }),
    );
  });

  afterEach(() => window.history.replaceState(null, '', '/'));

  it('matches a job and shows proven bullets, dropped claims and gaps', async () => {
    const user = userEvent.setup();
    render(<JobMatch />);

    await user.type(screen.getByLabelText('GitHub username'), 'Devisri4');
    await user.click(screen.getByRole('button', { name: 'Use an example job' }));
    await user.click(screen.getByRole('button', { name: 'Match my repos' }));

    expect(await screen.findByText('Built an online shop backend with Spring Boot and PostgreSQL.')).toBeInTheDocument();
    expect(screen.getByLabelText('1 of 3 requirements proven')).toBeInTheDocument();
    expect(screen.getByText('Code found, no bullet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'shop · pom.xml:5-10' }))
      .toHaveAttribute('href', 'https://github.com/Devisri4/shop/blob/main/backend/pom.xml#L5-L10');
    expect(screen.getByText('Deployed to Kubernetes.')).toBeInTheDocument();
    expect(screen.getByText(/cited evidence that does not exist/)).toBeInTheDocument();
    expect(screen.getAllByText('Kubernetes').length).toBeGreaterThanOrEqual(2); // requirement row + gap chip
    expect(screen.getByRole('button', { name: 'Copy share link' })).toBeInTheDocument();

    const [, options] = fetch.mock.calls.find(([url]) => String(url).endsWith('/api/match'));
    const sent = JSON.parse(options.body);
    expect(sent.githubUser).toBe('Devisri4');
    expect(sent.jobDescription).toContain('Spring Boot');
    expect(window.location.hash).toBe(`#match/${ID}`);
  });

  it('keeps the button disabled until a username and a real job description are given', async () => {
    const user = userEvent.setup();
    render(<JobMatch />);

    const submit = screen.getByRole('button', { name: 'Match my repos' });
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('GitHub username'), 'Devisri4');
    await user.type(screen.getByLabelText('Job description'), 'Java dev');
    expect(submit).toBeDisabled();
  });

  it('opens a shared result from the link', async () => {
    window.history.replaceState(null, '', `/#match/${ID}`);
    fetch.mockImplementation((url) => (String(url).endsWith(`/api/matches/${ID}`) ? json(200, RESULT) : json(404, {})));

    render(<JobMatch />);

    expect(await screen.findByText('Java Full Stack Developer')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining(`/api/matches/${ID}`), undefined);
  });

  it('shows the server message when something goes wrong', async () => {
    fetch.mockImplementation(() => json(400, { error: 'GitHub user nobody-xyz was not found.' }));
    const user = userEvent.setup();
    render(<JobMatch />);

    await user.type(screen.getByLabelText('GitHub username'), 'nobody-xyz');
    await user.click(screen.getByRole('button', { name: 'Use an example job' }));
    await user.click(screen.getByRole('button', { name: 'Match my repos' }));

    await waitFor(() => expect(screen.getByText('GitHub user nobody-xyz was not found.')).toBeInTheDocument());
  });
});
