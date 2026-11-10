import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom has no layout engine, so scrollIntoView does not exist there.
window.HTMLElement.prototype.scrollIntoView = vi.fn();

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
