/**
 * Custom Test Utilities
 * Provides test helpers and a custom render function that wraps components with providers
 */

import React, { ReactElement } from 'react';
import { render, RenderOptions } from '@testing-library/react';
import { ThemeProvider } from '@/context/ThemeContext';

/**
 * Custom render function that wraps components with necessary providers
 * This ensures components under test have access to Context providers, etc.
 */
interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  initialTheme?: 'light' | 'dark';
}

export function renderWithProviders(
  ui: ReactElement,
  {
    initialTheme = 'light',
    ...renderOptions
  }: CustomRenderOptions = {}
) {
  /**
   * Wrapper component that provides all necessary context and providers
   */
  function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <ThemeProvider defaultMode={initialTheme} persistToLocalStorage={false}>
        {children}
      </ThemeProvider>
    );
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
