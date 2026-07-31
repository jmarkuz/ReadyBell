/**
 * Layout Component Tests
 * Verifies Layout renders correctly with header, sidebar, and main content
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@/test/test-utils';
import Layout from './Layout';

describe('Layout Component', () => {
  it('renders layout without errors', () => {
    const { container } = render(<Layout>
      <div data-testid="test-content">Test Content</div>
    </Layout>);
    expect(container).toBeTruthy();
  });

  it('renders sidebar', () => {
    render(<Layout>
      <div>Test</div>
    </Layout>);
    const sidebar = screen.getByRole('navigation', { name: /main navigation/i });
    expect(sidebar).toBeInTheDocument();
  });

  it('renders header', () => {
    render(<Layout>
      <div>Test</div>
    </Layout>);
    const headers = screen.getAllByRole('banner');
    expect(headers.length).toBeGreaterThan(0);
  });

  it('renders main content area', () => {
    render(<Layout>
      <div data-testid="main-content">Main Content</div>
    </Layout>);
    const main = screen.getByRole('main');
    expect(main).toBeInTheDocument();
    expect(screen.getByTestId('main-content')).toBeInTheDocument();
  });

  it('displays children content', () => {
    render(<Layout>
      <div data-testid="child-content">Child Content</div>
    </Layout>);
    expect(screen.getByTestId('child-content')).toBeInTheDocument();
  });

  it('has proper responsive classes', () => {
    const { container } = render(<Layout>
      <div>Test</div>
    </Layout>);
    
    const layoutDiv = container.querySelector('[class*="flex"][class*="h-screen"]');
    expect(layoutDiv).toBeInTheDocument();
  });

  it('toggles sidebar visibility on mobile', () => {
    render(<Layout>
      <div>Test</div>
    </Layout>);
    
    const menuButton = screen.getByLabelText('Toggle menu');
    expect(menuButton).toBeInTheDocument();
  });
});
