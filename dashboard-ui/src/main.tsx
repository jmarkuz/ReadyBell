import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'

/**
 * Mock API (MSW) is opt-in: `VITE_USE_MOCKS=true npm run dev`.
 * By default the app talks to the real backend through the Vite dev proxy (see vite.config.ts).
 */
if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCKS === 'true') {
  const { worker } = await import('./mocks/browser');
  await worker.start({
    onUnhandledRequest: 'bypass', // Allow unhandled requests to pass through
  });
  console.info('[main] MSW mock API enabled - sign in with staff@restaurant.com / password');
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)

