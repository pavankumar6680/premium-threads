import { Boxes } from 'lucide-react';
import { Link } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { buttonClasses } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { formatDate, formatMoney, pluralize } from '../../lib/format';
import { tierLabels } from '../recommendations/api';
import { useMyBundles } from './api';

export function MyBundlesPage() {
  const { data, isPending, error } = useMyBundles();

  if (isPending) return <PageSpinner />;
  if (error) return <div className="mx-auto max-w-3xl px-4 py-10"><Alert tone="error">{error.message}</Alert></div>;

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">My bundles</h1>
      {data.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
          <Boxes className="mx-auto size-10 text-slate-300" aria-hidden="true" />
          <h2 className="mt-3 text-lg font-semibold">No bundles yet</h2>
          <p className="mt-1 text-sm text-slate-600">Customize a recommended bundle to save your own version.</p>
          <Link to="/goals" className={buttonClasses('primary', 'md', 'mt-5')}>Build a bundle</Link>
        </div>
      ) : (
        <ul className="mt-6 space-y-3">
          {data.map((b) => (
            <li key={b.id}>
              <Link to={`/bundles/${b.id}`} className="block rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-shadow hover:shadow-md">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold">{b.name}</span>
                    <span className={b.status === 'SAVED' ? 'rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800' : 'rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800'}>
                      {b.status === 'SAVED' ? 'Saved' : 'Draft'}
                    </span>
                  </div>
                  <span className="font-bold">{formatMoney(b.totalPrice)}</span>
                </div>
                <p className="mt-1 text-sm text-slate-600">
                  {b.goalTitle} · {tierLabels[b.tier]} · {pluralize(b.itemCount, 'item')} · budget {formatMoney(b.budget)} · updated {formatDate(b.updatedAt)}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
