import React, { createContext, useContext, useEffect, useState } from 'react';
import { Theme, ThemeContextType } from '@/types';

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

const STORAGE_KEY = 'readybell-theme';
const VALID_THEMES: Theme[] = ['light', 'dark'];

interface ThemeProviderProps {
  children: React.ReactNode;
  storageKey?: string;
  defaultMode?: Theme;
  persistToLocalStorage?: boolean;
}

export function ThemeProvider({
  children,
  storageKey = STORAGE_KEY,
  defaultMode = 'light',
  persistToLocalStorage = true,
}: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(() => {
    // Try to get from localStorage
    if (persistToLocalStorage) {
      try {
        const stored = localStorage.getItem(storageKey);
        if (stored && VALID_THEMES.includes(stored as Theme)) {
          return stored as Theme;
        }
      } catch (error) {
        console.warn('Failed to read theme from localStorage:', error);
      }
    }

    // Check system preference
    if (typeof window !== 'undefined' && window.matchMedia) {
      const prefersLight = !window.matchMedia('(prefers-color-scheme: dark)').matches;
      return prefersLight ? 'light' : 'dark';
    }

    return defaultMode;
  });

  // Update document class and data-theme attribute
  useEffect(() => {
    const root = document.documentElement;
    
    // Remove old theme class
    root.classList.remove('light', 'dark');
    
    // Add new theme class
    root.classList.add(theme);
    
    // Set data-theme attribute
    root.setAttribute('data-theme', theme);
  }, [theme]);

  // Persist to localStorage
  useEffect(() => {
    if (persistToLocalStorage) {
      try {
        localStorage.setItem(storageKey, theme);
      } catch (error) {
        console.warn('Failed to save theme to localStorage:', error);
      }
    }
  }, [theme, storageKey, persistToLocalStorage]);

  const toggleTheme = () => {
    setThemeState((prevTheme) => (prevTheme === 'light' ? 'dark' : 'light'));
  };

  const setTheme = (newTheme: Theme) => {
    if (VALID_THEMES.includes(newTheme)) {
      setThemeState(newTheme);
    }
  };

  const value: ThemeContextType = {
    theme,
    toggleTheme,
    setTheme,
    currentMode: theme,
  };

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextType {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}
