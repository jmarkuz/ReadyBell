import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { apiClient, type StaffAccountInfo } from '@/utils/api';
import { cardClass, inputClass, primaryButton, secondaryButton } from '@/components/ui';
import { useStaffStrings } from '@/i18n/useStaffStrings';

/** Restaurant admin: who can sign in to this restaurant's panel (US-A003). */
export default function Staff() {
  const [accounts, setAccounts] = useState<StaffAccountInfo[]>([]);
  const t = useStaffStrings();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'STAFF' | 'RESTAURANT_ADMIN'>('STAFF');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setAccounts(await apiClient.listStaff());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : t.couldNotLoadStaff);
    } finally {
      setLoading(false);
    }
  }, [t.couldNotLoadStaff]);

  useEffect(() => {
    void load();
  }, [load]);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await apiClient.createStaffAccount({ username: username.trim(), password, role });
      setUsername('');
      setPassword('');
      setRole('STAFF');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : t.couldNotCreate);
    } finally {
      setBusy(false);
    }
  };

  const deactivate = async (account: StaffAccountInfo) => {
    if (!window.confirm(`Deactivate ${account.username}? They will no longer be able to sign in.`)) return;
    setError(null);
    try {
      await apiClient.deactivateStaffAccount(account.accountId);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : t.couldNotDeactivate);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl text-slate-900 dark:text-white">{t.staffTitle}</h1>
        <p className="text-slate-600 dark:text-slate-400 mt-2">{t.staffIntro}</p>
      </div>

      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}

      <section className={cardClass} aria-labelledby="staff-list">
        <h2 id="staff-list" className="text-xl mb-4">{t.accounts}</h2>
        {loading ? (
          <p className="text-slate-500" role="status">{t.loading}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500">
                <th scope="col" className="py-2 font-medium">{t.username}</th>
                <th scope="col" className="py-2 font-medium">{t.role}</th>
                <th scope="col" className="py-2 font-medium">{t.status}</th>
                <th scope="col" className="py-2"><span className="sr-only">{t.colActions}</span></th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((a) => (
                <tr key={a.accountId} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="py-3 font-medium">{a.username}</td>
                  <td className="py-3">{a.role === 'RESTAURANT_ADMIN' ? t.roleAdmin : t.roleStaff}</td>
                  <td className="py-3">{a.status === 'ACTIVE' ? t.active : t.deactivated}</td>
                  <td className="py-3 text-right">
                    {a.status === 'ACTIVE' && (
                      <button type="button" className={secondaryButton} onClick={() => deactivate(a)} aria-label={`Deactivate ${a.username}`}>
                        {t.deactivate}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className={cardClass} aria-labelledby="staff-new">
        <h2 id="staff-new" className="text-xl mb-4">{t.addAccount}</h2>
        <form onSubmit={create} className="grid gap-4 sm:grid-cols-3 items-end">
          <div>
            <label htmlFor="staff-username" className="block text-sm font-medium mb-1">{t.username}</label>
            <input id="staff-username" className={inputClass} value={username} required autoComplete="off" onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div>
            <label htmlFor="staff-password" className="block text-sm font-medium mb-1">{t.passwordMin}</label>
            <input id="staff-password" className={inputClass} type="password" value={password} required minLength={10} maxLength={72} autoComplete="new-password" onChange={(e) => setPassword(e.target.value)} />
          </div>
          <div>
            <label htmlFor="staff-role" className="block text-sm font-medium mb-1">{t.role}</label>
            <select id="staff-role" className={inputClass} value={role} onChange={(e) => setRole(e.target.value as typeof role)}>
              <option value="STAFF">{t.roleStaffOption}</option>
              <option value="RESTAURANT_ADMIN">{t.roleAdminOption}</option>
            </select>
          </div>
          <div className="sm:col-span-3">
            <button type="submit" className={primaryButton} disabled={busy}>{busy ? t.adding : t.addAccountButton}</button>
          </div>
        </form>
      </section>
    </div>
  );
}
