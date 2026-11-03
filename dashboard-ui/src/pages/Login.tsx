import { FormEvent, useState } from 'react';
import { apiClient, ApiError } from '@/utils/api';

/**
 * Login page: exchanges username/password for a Bearer token via POST /api/login.
 * The token is stored by the API client; a full reload then lets AuthProvider restore the session.
 */
export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await apiClient.login({ username: username.trim(), password });
      window.location.href = '/';
    } catch (e) {
      setError(messageFor(e));
      setSubmitting(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-slate-900 to-slate-800">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md p-8 bg-white rounded-lg shadow-xl space-y-4"
        aria-labelledby="login-heading"
      >
        <h1 id="login-heading" className="text-2xl font-bold text-center text-slate-900 mb-4">
          ReadyBell Dashboard
        </h1>

        <div>
          <label htmlFor="username" className="block text-sm font-medium text-slate-700 mb-1">
            Username
          </label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700 mb-1">
            Password
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full py-2 px-4 bg-blue-600 text-white font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-60 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}

function messageFor(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.statusCode === 401) return 'Invalid username or password.';
    if (e.statusCode === 429) return 'Too many failed attempts. Please wait a few minutes and try again.';
  }
  return 'Sign-in failed. Check your connection and try again.';
}
