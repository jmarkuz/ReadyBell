import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE_URL || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    sourcemap: true,
    // Optimize chunk size and enable code splitting
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // Manual chunks for vendor libraries and features
        manualChunks: (id) => {
          // Vendor chunk for React ecosystem
          if (id.includes('node_modules/react')) {
            return 'vendor-react';
          }
          // UI libraries chunk
          if (id.includes('node_modules/lucide-react') || 
              id.includes('node_modules/clsx') ||
              id.includes('node_modules/date-fns')) {
            return 'vendor-ui';
          }
          // Router chunk
          if (id.includes('node_modules/react-router')) {
            return 'vendor-router';
          }
          // Context and utilities
          if (id.includes('src/context') || id.includes('src/utils')) {
            return 'app-shared';
          }
          // Page components get individual chunks
          if (id.includes('src/pages')) {
            const match = id.match(/pages\/([^/]+)\.tsx?/);
            if (match) {
              return `page-${match[1].toLowerCase()}`;
            }
          }
        },
        // Optimize entry point
        entryFileNames: 'js/[name]-[hash].js',
        chunkFileNames: 'js/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
    // Enable minification and compression
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
      },
    },
  },
  // Optimize dependency pre-bundling
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom'],
  },
})
