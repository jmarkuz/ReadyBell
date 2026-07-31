/**
 * Test Setup File
 * Runs before all tests and configures testing environment
 */

import '@testing-library/jest-dom';
import { afterEach, vi, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';

/**
 * Mock browser History API for tests
 * BrowserRouter requires History API which doesn't exist in happy-dom
 */
beforeAll(() => {
  // Mock window.history if not available
  if (!window.history) {
    Object.defineProperty(window, 'history', {
      value: {
        state: null,
        replaceState: vi.fn(),
        pushState: vi.fn(),
        back: vi.fn(),
        forward: vi.fn(),
        go: vi.fn(),
      },
      configurable: true,
    });
  }

  // Mock PopStateEvent if not available
  if (typeof PopStateEvent === 'undefined') {
    (global as any).PopStateEvent = class PopStateEvent extends Event {
      state: any;
      constructor(type: string, eventInitDict?: any) {
        super(type);
        this.state = eventInitDict?.state;
      }
    };
  }
});

/**
 * Cleanup after each test to avoid test pollution
 */
afterEach(() => {
  cleanup();
  // Clear localStorage between tests
  localStorage.clear();
});

/**
 * Mock window.matchMedia for theme preference detection
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

/**
 * Mock IntersectionObserver
 */
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  takeRecords() {
    return [];
  }
  unobserve() {}
} as any;
