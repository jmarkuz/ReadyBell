import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { apiClient, type RestaurantInfo } from '@/utils/api';
import { cardClass, inputClass, primaryButton, secondaryButton } from '@/components/ui';

/** Platform operator: onboard restaurants (with their first admin) and switch them off (US-P001). */
export default function Restaurants() {
  const [items, setItems] = useState<RestaurantInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [identifier, setIdentifier] = useState('');
  const [name, setName] = useState('');
  const [adminUsername, setAdminUsername] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setItems(await apiClient.listRestaurants());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load restaurants.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await apiClient.createRestaurant({
        identifier: identifier.trim(),
        name: name.trim(),
        adminUsername: adminUsername.trim() || undefined,
        adminPassword: adminUsername.trim() ? adminPassword : undefined,
      });
      setIdentifier(''); setName(''); setAdminUsername(''); setAdminPassword('');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the restaurant.');
    } finally {
      setBusy(false);
    }
  };

  const deactivate = async (r: RestaurantInfo) => {
    if (!window.confirm(`Deactivate ${r.name ?? r.identifier}? New customer sessions will be refused.`)) return;
    setError(null);
    try {
      await apiClient.deactivateRestaurant(r.tenantId);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not deactivate the restaurant.');
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl text-slate-900 dark:text-white">Restaurants</h1>
        <p className="text-slate-600 dark:text-slate-400 mt-2">Every restaurant on the platform.</p>
      </div>

      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}

      <section className={cardClass} aria-labelledby="restaurant-list">
        <h2 id="restaurant-list" className="text-xl mb-4">All restaurants</h2>
        {loading ? (
          <p className="text-slate-500" role="status">Loading…</p>
        ) : items.length === 0 ? (
          <p className="text-slate-500">No restaurants yet. Add the first one below.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500">
                <th scope="col" className="py-2 font-medium">Name</th>
                <th scope="col" className="py-2 font-medium">Identifier</th>
                <th scope="col" className="py-2 font-medium">Status</th>
                <th scope="col" className="py-2"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.tenantId} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="py-3 font-medium">{r.name ?? '—'}</td>
                  <td className="py-3 font-mono">{r.identifier}</td>
                  <td className="py-3">{r.status === 'ACTIVE' ? 'Active' : 'Deactivated'}</td>
                  <td className="py-3 text-right">
                    {r.status === 'ACTIVE' && (
                      <button type="button" className={secondaryButton} onClick={() => deactivate(r)} aria-label={`Deactivate ${r.name ?? r.identifier}`}>
                        Deactivate
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className={cardClass} aria-labelledby="restaurant-new">
        <h2 id="restaurant-new" className="text-xl mb-4">Add a restaurant</h2>
        <form onSubmit={create} className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="r-name" className="block text-sm font-medium mb-1">Name</label>
            <input id="r-name" className={inputClass} value={name} required maxLength={100} onChange={(e) => setName(e.target.value)} />
          </div>
          <div>
            <label htmlFor="r-identifier" className="block text-sm font-medium mb-1">Identifier (e.g. cafe-aurora)</label>
            <input id="r-identifier" className={inputClass} value={identifier} required onChange={(e) => setIdentifier(e.target.value)} />
          </div>
          <div>
            <label htmlFor="r-admin" className="block text-sm font-medium mb-1">First admin username <span className="font-normal text-slate-500">(optional)</span></label>
            <input id="r-admin" className={inputClass} value={adminUsername} autoComplete="off" onChange={(e) => setAdminUsername(e.target.value)} />
          </div>
          <div>
            <label htmlFor="r-pass" className="block text-sm font-medium mb-1">Admin password (min. 8 characters)</label>
            <input id="r-pass" className={inputClass} type="password" value={adminPassword} required={!!adminUsername.trim()} minLength={8} maxLength={72} autoComplete="new-password" onChange={(e) => setAdminPassword(e.target.value)} />
          </div>
          <div className="sm:col-span-2">
            <button type="submit" className={primaryButton} disabled={busy}>{busy ? 'Adding…' : 'Add restaurant'}</button>
          </div>
        </form>
      </section>
    </div>
  );
}
