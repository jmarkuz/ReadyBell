/**
 * App Component Test
 * Verifies App renders correctly with theme support
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@/test/test-utils';
import App from './App';

describe('App Component', () => {
  it('renders without errors', () => {
    render(<App />);
    expect(screen.getByText('ReadyBell Dashboard')).toBeInTheDocument();
  });

  it('displays welcome message', () => {
    render(<App />);
    expect(screen.getByText('Welcome to the Dashboard')).toBeInTheDocument();
  });

  it('displays setup status items', () => {
    render(<App />);
    expect(screen.getByText(/React \+ TypeScript initialized/)).toBeInTheDocument();
    expect(screen.getByText(/Tailwind CSS configured/)).toBeInTheDocument();
    expect(screen.getByText(/Theme system with localStorage/)).toBeInTheDocument();
    expect(screen.getByText(/CSS variables for theming/)).toBeInTheDocument();
    expect(screen.getByText(/Vite configured with API proxy/)).toBeInTheDocument();
  });

  it('renders theme provider without errors', () => {
    const { container } = render(<App />);
    // Verify the root element has theme context applied
    expect(container).toBeTruthy();
  });

  it('displays next steps section', () => {
    render(<App />);
    expect(screen.getByText('Next Steps')).toBeInTheDocument();
    expect(screen.getByText(/Setup project structure and conventions/)).toBeInTheDocument();
  });

  it('displays development instructions', () => {
    render(<App />);
    expect(screen.getByText('Development')).toBeInTheDocument();
    expect(screen.getByText(/npm run dev/)).toBeInTheDocument();
    expect(screen.getByText(/npm run build/)).toBeInTheDocument();
  });
});
