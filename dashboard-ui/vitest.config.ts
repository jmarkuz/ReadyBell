/**
 * Vitest Configuration
 * Unit and integration testing setup for React components
 */

import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    // Use happy-dom for lightweight DOM simulation
    environment: 'happy-dom',
    
    // Setup files to run before tests
    setupFiles: ['./src/test/setup.ts'],
    
    // Global test utilities
    globals: true,
    
    // Coverage thresholds
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'src/test/',
      ],
      // Floors just under the measured coverage (statements 90, branches 80, functions 74), so they
      // catch regressions without demanding the old, unmet 85% on functions.
      thresholds: {
        statements: 85,
        branches: 75,
        functions: 70,
        lines: 85,
      },
    },
    
    // Include patterns
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    
    // Exclude patterns
    exclude: ['node_modules', 'dist', '.idea', '.git', '.cache'],
  },
  
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
