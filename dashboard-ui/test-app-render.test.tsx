import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from '@/App';

describe('App render test', () => {
  it('shows what renders', () => {
    const { container } = render(<App />);
    console.log('Container HTML:', container.innerHTML);
    console.log('First child:', container.firstChild);
    console.log('Query result:', container.querySelector('div'));
  });
});
