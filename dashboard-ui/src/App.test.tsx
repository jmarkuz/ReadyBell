/**
 * App Component Test
 * Tests basic App structure (full integration test requires proper router setup)
 */

import { describe, it, expect } from 'vitest';
import App from './App';

describe('App Component', () => {
  it('App component is defined and exportable', () => {
    expect(App).toBeDefined();
    expect(typeof App).toBe('function');
  });

  it('App component has the correct name', () => {
    expect(App.name).toBe('App');
  });
});
