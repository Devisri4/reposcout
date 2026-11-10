import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import CitationList from './CitationList.jsx';

const citations = [
  { path: 'src/Owner.java', startLine: 52, endLine: 65, verified: true, reason: null, url: 'https://github.com/a/b/blob/main/src/Owner.java#L52-L65' },
  { path: 'src/Fake.java', startLine: 1, endLine: 1, verified: false, reason: 'file does not exist in the repository', url: null },
];

describe('CitationList', () => {
  it('links verified files to the exact GitHub lines', () => {
    render(<CitationList citations={citations} />);

    const link = screen.getByRole('link', { name: 'src/Owner.java:52-65' });
    expect(link).toHaveAttribute('href', 'https://github.com/a/b/blob/main/src/Owner.java#L52-L65');
    expect(link).toHaveAttribute('target', '_blank');
    expect(screen.getByText('1 verified')).toBeInTheDocument();
  });

  it('shows removed references with the reason and no link', () => {
    render(<CitationList citations={citations} />);

    expect(screen.getByText('1 removed')).toBeInTheDocument();
    expect(screen.getByText('src/Fake.java:1')).toBeInTheDocument();
    expect(screen.getByText(/file does not exist in the repository/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'src/Fake.java:1' })).not.toBeInTheDocument();
  });

  it('says so when the answer cited nothing', () => {
    render(<CitationList citations={[]} />);

    expect(screen.getByText('The answer did not cite any files.')).toBeInTheDocument();
  });
});
