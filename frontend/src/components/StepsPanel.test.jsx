import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import StepsPanel from './StepsPanel.jsx';

describe('StepsPanel', () => {
  it('lists every tool call in order with its result', () => {
    render(
      <StepsPanel
        steps={[
          { number: 1, tool: 'searchCode', input: '"Owner"', summary: '640 match(es)', millis: 12 },
          { number: 2, tool: 'readFile', input: 'src/Owner.java lines 1-60', summary: 'read lines 1-60', millis: 3 },
          { number: 3, tool: 'readFile', input: 'src/Nope.java lines 1-5', summary: "Error: file 'src/Nope.java' does not exist.", millis: 1 },
        ]}
      />,
    );

    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent('searchCode');
    expect(items[0]).toHaveTextContent('640 match(es)');
    expect(items[1]).toHaveTextContent('src/Owner.java lines 1-60');
    expect(screen.getByText(/does not exist/)).toHaveClass('bad');
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});
