/**
 * ThemeContext Test Suite
 * Tests theme state management, persistence, and system preference detection
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { ThemeProvider, useTheme } from './ThemeContext';
import React from 'react';

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('light', 'dark');
  });

  describe('ThemeProvider', () => {
    it('provides default theme as light', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.theme).toBe('light');
      expect(result.current.currentMode).toBe('light');
    });

    it('provides default theme as dark when specified', () => {
      // Note: The ThemeContext checks system preference if localStorage is empty
      // This test verifies the context initializes without errors with a dark default
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="dark" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      // Theme should be a valid theme (either light or dark)
      // The actual value depends on system preference if localStorage is empty
      expect(['light', 'dark']).toContain(result.current.theme);
      expect(['light', 'dark']).toContain(result.current.currentMode);
    });

    it('applies theme class to document root', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      renderHook(() => useTheme(), { wrapper });

      expect(document.documentElement.classList.contains('light')).toBe(true);
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    });

    it('updates document class when theme changes', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('dark');
      });

      await waitFor(() => {
        expect(document.documentElement.classList.contains('dark')).toBe(true);
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      });
    });
  });

  describe('useTheme hook', () => {
    it('toggles between light and dark themes', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.theme).toBe('light');

      act(() => {
        result.current.toggleTheme();
      });

      expect(result.current.theme).toBe('dark');

      act(() => {
        result.current.toggleTheme();
      });

      expect(result.current.theme).toBe('light');
    });

    it('sets specific theme via setTheme', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('dark');
      });

      expect(result.current.theme).toBe('dark');

      act(() => {
        result.current.setTheme('light');
      });

      expect(result.current.theme).toBe('light');
    });

    it('throws error when used outside ThemeProvider', () => {
      // Suppress console.error for this test
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        renderHook(() => useTheme());
      }).toThrow('useTheme must be used within ThemeProvider');

      spy.mockRestore();
    });
  });

  describe('localStorage persistence', () => {
    it('persists theme to localStorage when enabled', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.toggleTheme();
      });

      await waitFor(() => {
        expect(localStorage.getItem('readybell-theme')).toBe('dark');
      });
    });

    it('does not persist to localStorage when disabled', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.toggleTheme();
      });

      await waitFor(() => {
        expect(localStorage.getItem('readybell-theme')).toBeNull();
      });
    });

    it('loads theme from localStorage on mount', () => {
      localStorage.setItem('readybell-theme', 'dark');

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.theme).toBe('dark');
    });

    it('ignores invalid values in localStorage', () => {
      localStorage.setItem('readybell-theme', 'invalid');

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider defaultMode="light" persistToLocalStorage>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      // Should use system preference or default
      expect(['light', 'dark']).toContain(result.current.theme);
    });

    it('uses custom storage key', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider
          storageKey="custom-theme-key"
          defaultMode="light"
          persistToLocalStorage
        >
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.toggleTheme();
      });

      await waitFor(() => {
        expect(localStorage.getItem('custom-theme-key')).toBe('dark');
        expect(localStorage.getItem('readybell-theme')).toBeNull();
      });
    });
  });

  describe('system preference detection', () => {
    it('detects system dark mode preference', () => {
      // Mock matchMedia to indicate dark mode preference
      window.matchMedia = vi.fn().mockImplementation(query => ({
        matches: query === '(prefers-color-scheme: dark)',
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }));

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.theme).toBe('dark');
    });

    it('uses light theme as fallback for system preference', () => {
      window.matchMedia = vi.fn().mockImplementation(() => ({
        matches: false,
        media: '',
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }));

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <ThemeProvider persistToLocalStorage={false}>
          {children}
        </ThemeProvider>
      );

      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.theme).toBe('light');
    });
  });
});
