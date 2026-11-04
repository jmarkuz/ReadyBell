import { useEffect, useState, type FormEvent } from 'react';
import { apiClient } from '@/utils/api';
import { useRestaurant } from '@/context/restaurantContext';
import { cardClass, inputClass, primaryButton } from '@/components/ui';

/** Restaurant admin: the name customers see in the panel header and on their tracking page. */
export default function Settings() {
  const restaurant = useRestaurant();
  const [name, setName] = useState(restaurant.name);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);

  useEffect(() => setName(restaurant.name), [restaurant.name]);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await apiClient.updateRestaurantConfig({ name: name.trim() });
      await restaurant.refresh();
      setMessage({ text: 'Saved.', error: false });
    } catch (err) {
      setMessage({ text: err instanceof Error ? err.message : 'Could not save.', error: true });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl text-slate-900 dark:text-white">Settings</h1>
        <p className="text-slate-600 dark:text-slate-400 mt-2">Your restaurant's public details.</p>
      </div>

      <section className={`${cardClass} max-w-xl`} aria-labelledby="restaurant-details">
        <h2 id="restaurant-details" className="text-xl mb-4">Restaurant</h2>
        <form onSubmit={save} className="space-y-4">
          <div>
            <label htmlFor="restaurant-name" className="block text-sm font-medium mb-1">Name shown to customers</label>
            <input id="restaurant-name" className={inputClass} value={name} required maxLength={100} onChange={(e) => setName(e.target.value)} />
          </div>
          {restaurant.identifier && (
            <p className="text-sm text-slate-500">Identifier: <span className="font-mono">{restaurant.identifier}</span></p>
          )}
          <div className="flex items-center gap-4">
            <button type="submit" className={primaryButton} disabled={busy || !name.trim()}>{busy ? 'Saving…' : 'Save'}</button>
            {message && <span role={message.error ? 'alert' : 'status'} className={`text-sm ${message.error ? 'text-red-600' : 'text-slate-600'}`}>{message.text}</span>}
          </div>
        </form>
      </section>
    </div>
  );
}
