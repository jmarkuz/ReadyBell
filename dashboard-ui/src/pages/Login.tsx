import { FormEvent, useState } from 'react';
import { apiClient, ApiError } from '@/utils/api';
import { staffStrings } from '@/i18n/staff';
import { useStaffStrings } from '@/i18n/useStaffStrings';

const RESTAURANT_KEY = 'readybell.login.restaurant';

function rememberedRestaurant(): string {
  try {
    return window.localStorage.getItem(RESTAURANT_KEY) ?? '';
  } catch {
    return '';
  }
}

function rememberRestaurant(value: string) {
  try {
    if (value) window.localStorage.setItem(RESTAURANT_KEY, value);
    else window.localStorage.removeItem(RESTAURANT_KEY);
  } catch {
    /* private window or blocked storage: the field is just empty next time */
  }
}

/**
 * Login page: exchanges restaurant + username + password for a Bearer token via POST /api/login.
 * Usernames repeat between restaurants, so the restaurant (its identifier) is part of the sign-in; the platform
 * operator leaves it empty. The last restaurant is remembered in this browser.
 * The token is stored by the API client; a full reload then lets AuthProvider restore the session.
 */
export default function Login() {
  const t = useStaffStrings();
  const [restaurant, setRestaurant] = useState(rememberedRestaurant);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const restaurantId = restaurant.trim();
      await apiClient.login({ ...(restaurantId && { restaurant: restaurantId }), username: username.trim(), password });
      rememberRestaurant(restaurantId);
      // only the customer display may be returned to; anything else would be an open redirect
      window.location.href = new URLSearchParams(window.location.search).get('next') === '/display' ? '/display' : '/';
    } catch (e) {
      setError(messageFor(e));
      setSubmitting(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-[#f5f5f5] atmosphere">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md p-8 bg-white rounded-xl border border-slate-200 shadow-md space-y-4"
        aria-labelledby="login-heading"
      >
        <h1 id="login-heading" className="text-3xl text-center text-slate-900 mb-4">
          ReadyBell Dashboard
        </h1>

        <div>
          <label htmlFor="restaurant" className="block text-sm font-medium text-slate-700 mb-1">
            {t.loginRestaurant}
          </label>
          <input
            id="restaurant"
            name="restaurant"
            autoComplete="organization"
            autoCapitalize="none"
            spellCheck={false}
            value={restaurant}
            onChange={(e) => setRestaurant(e.target.value)}
            aria-describedby="restaurant-hint"
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <p id="restaurant-hint" className="mt-1 text-xs text-slate-500">
            {t.loginRestaurantHint}
          </p>
        </div>

        <div>
          <label htmlFor="username" className="block text-sm font-medium text-slate-700 mb-1">
            {t.loginUsername}
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
            {t.loginPassword}
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
          className="w-full h-10 px-5 bg-blue-600 text-white font-medium rounded-full hover:bg-blue-700 disabled:opacity-60 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          {submitting ? t.loginSigningIn : t.loginSignIn}
        </button>
      </form>
    </div>
  );
}

function messageFor(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.statusCode === 401) return staffStrings().loginInvalid;
    if (e.statusCode === 429) return staffStrings().loginThrottled;
  }
  return staffStrings().loginFailed;
}
