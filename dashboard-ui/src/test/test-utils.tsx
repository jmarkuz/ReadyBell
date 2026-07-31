/**
 * Custom Test Utilities
 * Provides test helpers and a custom render function that wraps components with providers
 */

import React, { ReactElement } from 'react';
import { render, RenderOptions } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';

/**
 * Custom render function that wraps components with necessary providers
 * This ensures components under test have access to Context providers, etc.
 */
interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  initialTheme?: 'light' | 'dark';
  withRouter?: boolean;
}

export function renderWithProviders(
  ui: ReactElement,
  {
    initialTheme = 'light',
    withRouter = true,
    ...renderOptions
  }: CustomRenderOptions = {}
) {
  /**
   * Wrapper component that provides all necessary context and providers
   */
  function Wrapper({ children }: { children: React.ReactNode }) {
    const content = (
      <ThemeProvider defaultMode={initialTheme} persistToLocalStorage={false}>
        {children}
      </ThemeProvider>
    );

    if (withRouter) {
      return <MemoryRouter initialEntries={['/']}>{content}</MemoryRouter>;
    }

    return content;
  }

  return render(ui, { wrapper: Wrapper, ...renderOptions });
}

/**
 * Re-export common testing utilities
 */
export * from '@testing-library/react';

/**
 * Override render with custom version
 */
export { renderWithProviders as render };
