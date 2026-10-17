import { Package } from 'lucide-react';
import { Link, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { buttonClasses } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { PageSpinner } from '../../components/ui/Spinner';
import { formatDate, formatMoney, pluralize } from '../../lib/format';
import { useOrders } from './api';
import { OrderStatusBadge } from './OrderStatusBadge';

export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);
  const { data, isPending, error } = useOrders(page);

  if (isPending) return <PageSpinner />;
  if (error) return <div className="mx-auto max-w-3xl px-4 py-10"><Alert tone="error">{error.message}</Alert></div>;

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">My orders</h1>

      {data.totalElements === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
          <Package className="mx-auto size-10 text-slate-300" aria-hidden="true" />
          <h2 className="mt-3 text-lg font-semibold">No orders yet</h2>
          <p className="mt-1 text-sm text-slate-600">When you place an order it will show up here.</p>
          <Link to="/shop" className={buttonClasses('primary', 'md', 'mt-5')}>Start shopping</Link>
        </div>
      ) : (
        <>
          <ul className="mt-6 space-y-3">
            {data.content.map((order) => (
              <li key={order.id}>
                <Link
                  to={`/orders/${order.id}`}
                  className="block rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-shadow hover:shadow-md"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-3">
                      <span className="font-mono text-sm font-semibold">{order.orderNumber}</span>
                      <OrderStatusBadge status={order.status} />
                    </div>
                    <span className="font-bold">{formatMoney(order.total, order.currency)}</span>
                  </div>
                  <p className="mt-1 text-sm text-slate-600">
                    {formatDate(order.createdAt)} · {pluralize(order.itemCount, 'item')}
                  </p>
                  <p className="mt-1 truncate text-sm text-slate-500">
                    {order.itemNames.join(', ')}
                    {order.itemCount > order.itemNames.length && '…'}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onChange={(p) => setParams(p > 0 ? { page: String(p) } : {})}
          />
        </>
      )}
    </div>
  );
}
