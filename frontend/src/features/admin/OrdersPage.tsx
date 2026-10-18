import clsx from 'clsx';
import { Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { formatDateTime, formatMoney } from '../../lib/format';
import { OrderStatusBadge } from '../orders/OrderStatusBadge';
import { statusLabels, type OrderStatus } from '../orders/api';
import { useAdminOrders } from './api';
import { PageHeader, TableWrap, td, th } from './components/AdminUi';

export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const orderNumber = params.get('orderNumber') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [search, setSearch] = useState(orderNumber);
  const { data, isPending, isError } = useAdminOrders(status, orderNumber, page);

  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };
  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    set({ orderNumber: search.trim() || null, status: null });
  };

  return (
    <>
      <PageHeader title="Orders" description="Ship paid orders, mark deliveries and cancel orders that have not shipped yet." />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-1 rounded-lg bg-slate-100 p-1">
          {(['', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'] as const).map((s) => (
            <button
              key={s || 'all'}
              type="button"
              role="tab"
              aria-selected={status === s}
              onClick={() => set({ status: s || null, orderNumber: null })}
              className={clsx(
                'rounded-md px-3 py-1.5 text-sm font-medium',
                status === s ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
              )}
            >
              {s ? statusLabels[s as OrderStatus].label : 'All'}
            </button>
          ))}
        </div>
        <form role="search" onSubmit={onSearch} className="relative min-w-48 flex-1 sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <input
            type="search"
            aria-label="Find by order number"
            placeholder="Order number, e.g. BMB-…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none"
          />
        </form>
      </div>
      {isPending ? (
        <div className="flex justify-center py-16"><Spinner /></div>
      ) : isError ? (
        <Alert tone="error">Orders could not be loaded.</Alert>
      ) : data.content.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-slate-300 p-10 text-center text-sm text-slate-500">No orders found.</p>
      ) : (
        <>
          <TableWrap>
            <thead className="bg-slate-50">
              <tr>
                <th className={th}>Order</th>
                <th className={th}>Customer</th>
                <th className={th}>Placed</th>
                <th className={th}>Status</th>
                <th className={clsx(th, 'text-right')}>Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((o) => (
                <tr key={o.id}>
                  <td className={td}>
                    <Link to={`/admin/orders/${o.id}`} className="font-semibold text-slate-900 hover:text-brand-700 hover:underline">{o.orderNumber}</Link>
                    <p className="text-xs text-slate-500">{o.itemCount} items</p>
                  </td>
                  <td className={td}>
                    <p className="text-slate-900">{o.customer?.fullName ?? 'Unknown'}</p>
                    <p className="text-xs text-slate-500">{o.customer?.email}</p>
                  </td>
                  <td className={clsx(td, 'whitespace-nowrap')}>{formatDateTime(o.createdAt)}</td>
                  <td className={td}><OrderStatusBadge status={o.status} /></td>
                  <td className={clsx(td, 'whitespace-nowrap text-right font-medium')}>{formatMoney(o.total, o.currency)}</td>
                </tr>
              ))}
            </tbody>
          </TableWrap>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => set({ page: String(p) })} />
        </>
      )}
    </>
  );
}
