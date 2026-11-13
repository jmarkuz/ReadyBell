import { Coffee } from 'lucide-react';
import { displayFont } from '@/components/customerStyles';

/** Frame shared by the customer pages (tracking and counter claim): restaurant header, optional live badge. */
export function CustomerShell({ children, restaurantName, live, liveLabel }: {
  children: React.ReactNode; restaurantName?: string; live?: boolean; liveLabel?: string;
}) {
  return (
    <div className="min-h-screen bg-[#f5f5f5] text-stone-900">
      <header className="px-4 pt-6 pb-4 max-w-md mx-auto flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-stone-800 text-white flex items-center justify-center" aria-hidden="true">
          <Coffee className="w-5 h-5" />
        </div>
        <h1 className="text-2xl flex-1 truncate" style={displayFont}>{restaurantName || 'ReadyBell'}</h1>
        {liveLabel && (
          <span className="text-xs text-stone-600 flex items-center gap-1.5" role="status">
            <span className={`w-2 h-2 rounded-full ${live ? 'bg-green-500' : 'bg-stone-400'}`} aria-hidden="true" />
            {liveLabel}
          </span>
        )}
      </header>
      <main className="px-4 pb-12 max-w-md mx-auto">{children}</main>
    </div>
  );
}
