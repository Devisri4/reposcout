import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';

const answer = {
  repo: 'spring-projects/spring-petclinic',
  branch: 'main',
  answer: 'Owners are saved in [src/OwnerController.java:84](https://github.com/x/y/blob/main/src/OwnerController.java#L84).',
  citations: [{ path: 'src/OwnerController.java', startLine: 84, endLine: 84, verified: true, reason: null, url: 'https://github.com/x/y/blob/main/src/OwnerController.java#L84' }],
  steps: [{ number: 1, tool: 'readFile', input: 'src/OwnerController.java lines 1-120', summary: 'read lines 1-120', millis: 5 }],
  toolCalls: 1,
};

function json(status, body) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('App', () => {
  beforeEach(() => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url) =>
      String(url).endsWith('/actuator/health') ? json(200, { status: 'UP' }) : json(200, answer),
    );
  });

  it('shows the server is ready once the health check answers', async () => {
    render(<App />);

    expect(await screen.findByText('Server ready')).toBeInTheDocument();
  });

  it('asks the question and shows the answer, file links and steps', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.type(screen.getByLabelText('Question'), 'How are owners saved?');
    await user.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText(/Owners are saved in/)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'src/OwnerController.java:84' })[0])
      .toHaveAttribute('href', 'https://github.com/x/y/blob/main/src/OwnerController.java#L84');
    expect(screen.getByText('1 verified')).toBeInTheDocument();
    expect(screen.getByText(/1 tool calls/)).toBeInTheDocument();

    const askCall = fetch.mock.calls.find(([url]) => String(url).endsWith('/api/ask'));
    expect(JSON.parse(askCall[1].body)).toEqual({
      repoUrl: 'https://github.com/spring-projects/spring-petclinic',
      question: 'How are owners saved?',
    });
  });

  it('shows the server error message, e.g. when the visitor limit is reached', async () => {
    fetch.mockImplementation((url) =>
      String(url).endsWith('/actuator/health')
        ? json(200, { status: 'UP' })
        : json(429, { error: "You've asked 10 questions in the last hour." }),
    );
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'How is a new owner validated and saved?' }));

    await waitFor(() => expect(screen.getByText("You've asked 10 questions in the last hour.")).toBeInTheDocument());
  });
});
