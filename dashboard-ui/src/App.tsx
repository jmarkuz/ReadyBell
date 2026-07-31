import { ThemeProvider } from '@/context/ThemeContext';
import './App.css';

function App() {
  return (
    <ThemeProvider defaultMode="light" persistToLocalStorage>
      <div className="min-h-screen bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-50">
        <header className="bg-blue-600 dark:bg-blue-700 text-white p-4">
          <h1 className="text-2xl font-bold">ReadyBell Dashboard</h1>
          <p className="text-sm mt-1">Restaurant Order Management System</p>
        </header>
        
        <main className="container mx-auto p-6">
          <div className="bg-white dark:bg-slate-900 rounded-lg shadow-md p-6">
            <h2 className="text-xl font-bold mb-4">Welcome to the Dashboard</h2>
            <p className="text-gray-600 dark:text-gray-300 mb-4">
              This is the ReadyBell restaurant dashboard with light/dark theme support.
            </p>
            
            <section className="mt-6 p-4 bg-blue-50 dark:bg-slate-800 rounded border border-blue-200 dark:border-slate-700">
              <h3 className="font-semibold text-blue-900 dark:text-blue-300 mb-2">Setup Status</h3>
              <ul className="list-disc list-inside space-y-2 text-sm">
                <li className="text-green-700 dark:text-green-400">✓ React + TypeScript initialized</li>
                <li className="text-green-700 dark:text-green-400">✓ Tailwind CSS configured</li>
                <li className="text-green-700 dark:text-green-400">✓ Theme system with localStorage</li>
                <li className="text-green-700 dark:text-green-400">✓ CSS variables for theming</li>
                <li className="text-green-700 dark:text-green-400">✓ Vite configured with API proxy</li>
              </ul>
            </section>

            <section className="mt-6 p-4 bg-amber-50 dark:bg-slate-800 rounded border border-amber-200 dark:border-slate-700">
              <h3 className="font-semibold text-amber-900 dark:text-amber-300 mb-2">Next Steps</h3>
              <ul className="list-disc list-inside space-y-2 text-sm">
                <li>Task 1.2: Setup project structure and conventions</li>
                <li>Task 1.3: Create CSS variables and design tokens</li>
                <li>Task 1.4: Setup theme context (DONE)</li>
                <li>Task 2: Build core layout components</li>
              </ul>
            </section>

            <section className="mt-6">
              <h3 className="font-semibold mb-3">Development</h3>
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                Start the dev server with: <code className="bg-gray-200 dark:bg-slate-700 px-2 py-1 rounded">npm run dev</code>
              </p>
              <p className="text-sm text-gray-600 dark:text-gray-400">
                Build for production: <code className="bg-gray-200 dark:bg-slate-700 px-2 py-1 rounded">npm run build</code>
              </p>
            </section>
          </div>
        </main>
      </div>
    </ThemeProvider>
  );
}

export default App;
