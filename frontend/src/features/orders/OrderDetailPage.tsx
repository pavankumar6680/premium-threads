import { CircleCheck } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button, buttonClasses } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { ApiError } from '../../lib/api';
import { formatDateTime, formatMoney } from '../../lib/format';
import { TotalsCard } from '../cart/TotalsCard';
import { useCancelOrder, useOrder } from './api';
import { OrderStatusBadge } from './OrderStatusBadge';

export function OrderDetailPage() {
  const { id } = useParams();
  const orderId = Number(id);
  const location = useLocation();
  const justPlaced = (location.state as { placed?: boolean } | null)?.placed === true;
  const { data: order, isPending, error } = useOrder(orderId);
  const cancel = useCancelOrder();
  const toast = useToast();

  if (isPending) return <PageSpinner />;
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="text-2xl font-bold">{notFound ? 'Order not found' : 'Something went wrong'}</h1>
        <Link to="/orders" className={buttonClasses('primary', 'md', 'mt-6')}>View my orders</Link>
      </div>
    );
  }

  const onCancel = () => {
    if (!window.confirm(`Cancel order ${order.orderNumber}? Your payment will be refunded.`)) return;
    cancel.mutate(order.id, {
      onSuccess: () => toast.show('Order cancelled and refunded'),
      onError: (e) => toast.show(e instanceof ApiError ? e.message : 'Could not cancel the order', 'error'),
    });
  };

  const address = order.shippingAddress;

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      {justPlaced && (
        <div className="mb-6 flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-emerald-900">
          <CircleCheck className="mt-0.5 size-6 shrink-0 text-emerald-600" aria-hidden="true" />
          <div>
            <h1 className="text-lg font-bold">Thank you! Your order is confirmed.</h1>
            <p className="text-sm">Order {order.orderNumber} has been placed and paid. We’ll let you know when it ships.</p>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link to="/orders" className="text-sm font-semibold text-brand-700 hover:underline">← All orders</Link>
          <div className="mt-2 flex items-center gap-3">
            {justPlaced ? (
              <h2 className="font-mono text-2xl font-bold">{order.orderNumber}</h2>
            ) : (
              <h1 className="font-mono text-2xl font-bold">{order.orderNumber}</h1>
            )}
            <OrderStatusBadge status={order.status} />
          </div>
          <p className="mt-1 text-sm text-slate-600">Placed {formatDateTime(order.createdAt)}</p>
          {order.cancelledAt && <p className="text-sm text-slate-600">Cancelled {formatDateTime(order.cancelledAt)}</p>}
        </div>
        {order.cancellable && (
          <Button variant="secondary" onClick={onCancel} isLoading={cancel.isPending}>Cancel order</Button>
        )}
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_340px]">
        <section className="rounded-2xl border border-slate-200 bg-white" aria-label="Items">
          <ul className="divide-y divide-slate-100">
            {order.items.map((item) => (
              <li key={item.variantId ?? item.productId} className="flex justify-between gap-4 p-4 text-sm">
                <div>
                  <p className="text-xs uppercase tracking-wide text-slate-500">{item.brand}</p>
                  {item.slug ? (
                    <Link to={`/products/${item.slug}`} className="font-semibold hover:text-brand-700">{item.name}</Link>
                  ) : (
                    <p className="font-semibold">{item.name}</p>
                  )}
                  {(item.size || item.color) && <p className="text-slate-700">{[item.color, item.size].filter(Boolean).join(' · ')}</p>}
                  <p className="text-slate-600">{item.quantity} × {formatMoney(item.unitPrice, order.currency)}</p>
                </div>
                <p className="font-semibold">{formatMoney(item.lineTotal, order.currency)}</p>
              </li>
            ))}
          </ul>
        </section>

        <div className="space-y-4">
          <TotalsCard subtotal={order.subtotal} shipping={order.shipping} tax={order.tax} total={order.total} currency={order.currency} />
          <section className="rounded-2xl border border-slate-200 bg-white p-5 text-sm">
            <h2 className="font-semibold">Shipping to</h2>
            <address className="mt-2 not-italic text-slate-700">
              {address.fullName}<br />
              {address.line1}<br />
              {address.line2 && <>{address.line2}<br /></>}
              {address.city}, {address.state} {address.postalCode}<br />
              {address.country}
            </address>
          </section>
          {order.payment && (
            <section className="rounded-2xl border border-slate-200 bg-white p-5 text-sm">
              <h2 className="font-semibold">Payment</h2>
              <p className="mt-2 text-slate-700">Simulated card · {order.payment.status.toLowerCase()}</p>
              <p className="font-mono text-xs text-slate-500">{order.payment.reference}</p>
            </section>
          )}
          {order.status === 'CANCELLED' && <Alert tone="info">This order was cancelled and the payment refunded.</Alert>}
        </div>
      </div>
    </div>
  );
}
