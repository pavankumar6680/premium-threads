import { ArrowLeft, Ban, PackageCheck, Truck } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDateTime, formatMoney } from '../../lib/format';
import { OrderStatusBadge } from '../orders/OrderStatusBadge';
import type { OrderStatus } from '../orders/api';
import { useAdminOrder, useChangeOrderStatus } from './api';
import { Card, errorMessage } from './components/AdminUi';

const ACTIONS: Record<string, { label: string; icon: typeof Truck; variant: 'primary' | 'secondary'; done: string }> = {
  SHIPPED: { label: 'Mark as shipped', icon: Truck, variant: 'primary', done: 'Order marked as shipped.' },
  DELIVERED: { label: 'Mark as delivered', icon: PackageCheck, variant: 'primary', done: 'Order marked as delivered.' },
  CANCELLED: { label: 'Cancel and refund', icon: Ban, variant: 'secondary', done: 'Order cancelled, stock returned and payment refunded.' },
};

export function AdminOrderPage() {
  const id = Number(useParams().id);
  const { data, isPending, isError } = useAdminOrder(id);
  const change = useChangeOrderStatus();
  const toast = useToast();
  const [confirmCancel, setConfirmCancel] = useState(false);

  if (isPending) return <PageSpinner />;
  if (isError) return <Alert tone="error">This order could not be loaded.</Alert>;
  const { order, customer, allowedTransitions } = data;

  const run = (status: OrderStatus) =>
    change.mutate(
      { id, status },
      {
        onSuccess: () => {
          setConfirmCancel(false);
          toast.show(ACTIONS[status].done);
        },
      },
    );

  return (
    <>
      <Link to="/admin/orders" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-slate-600 hover:text-slate-900">
        <ArrowLeft className="size-4" aria-hidden="true" /> Orders
      </Link>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-3 text-2xl font-bold tracking-tight text-slate-900">
            {order.orderNumber} <OrderStatusBadge status={order.status} />
          </h1>
          <p className="mt-1 text-sm text-slate-600">Placed {formatDateTime(order.createdAt)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {allowedTransitions.map((status) => {
            const action = ACTIONS[status];
            const Icon = action.icon;
            if (status === 'CANCELLED' && !confirmCancel) {
              return (
                <Button key={status} variant="secondary" onClick={() => setConfirmCancel(true)}>
                  <Icon className="size-4" aria-hidden="true" /> {action.label}
                </Button>
              );
            }
            return (
              <Button key={status} variant={action.variant} onClick={() => run(status)} isLoading={change.isPending && change.variables?.status === status}>
                <Icon className="size-4" aria-hidden="true" /> {status === 'CANCELLED' ? 'Confirm cancellation' : action.label}
              </Button>
            );
          })}
        </div>
      </div>
      {confirmCancel && (
        <div className="mb-4">
          <Alert tone="info">The customer is refunded {formatMoney(order.total, order.currency)} and the items go back into stock. This cannot be undone.</Alert>
        </div>
      )}
      {change.error && <div className="mb-4"><Alert tone="error">{errorMessage(change.error)}</Alert></div>}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Items" className="lg:col-span-2">
          <ul className="divide-y divide-slate-100">
            {order.items.map((item) => (
              <li key={item.productId} className="flex items-center justify-between gap-4 py-3 text-sm">
                <div className="min-w-0">
                  <Link to={`/admin/products/${item.productId}`} className="font-medium text-slate-900 hover:underline">{item.name}</Link>
                  <p className="text-xs text-slate-500">{item.sku} · {item.quantity} × {formatMoney(item.unitPrice, order.currency)}</p>
                </div>
                <span className="font-medium text-slate-900">{formatMoney(item.lineTotal, order.currency)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-4 space-y-1 border-t border-slate-200 pt-4 text-sm">
            <Row label="Subtotal" value={formatMoney(order.subtotal, order.currency)} />
            <Row label="Shipping" value={formatMoney(order.shipping, order.currency)} />
            <Row label="Tax" value={formatMoney(order.tax, order.currency)} />
            <Row label="Total" value={formatMoney(order.total, order.currency)} strong />
          </dl>
        </Card>
        <div className="space-y-6">
          <Card title="Customer">
            <p className="text-sm font-medium text-slate-900">{customer?.fullName ?? 'Unknown customer'}</p>
            {customer && <a href={`mailto:${customer.email}`} className="text-sm text-brand-700 hover:underline">{customer.email}</a>}
          </Card>
          <Card title="Ship to">
            <address className="text-sm not-italic leading-6 text-slate-700">
              {order.shippingAddress.fullName}<br />
              {order.shippingAddress.line1}<br />
              {order.shippingAddress.line2 && <>{order.shippingAddress.line2}<br /></>}
              {order.shippingAddress.city}, {order.shippingAddress.state} {order.shippingAddress.postalCode}<br />
              {order.shippingAddress.country}
              {order.shippingAddress.phone && <><br />{order.shippingAddress.phone}</>}
            </address>
          </Card>
          {order.payment && (
            <Card title="Payment">
              <p className="text-sm text-slate-700">{order.payment.method} · {order.payment.status.toLowerCase()}</p>
              <p className="text-xs text-slate-500">Reference {order.payment.reference}</p>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={strong ? 'flex justify-between font-semibold text-slate-900' : 'flex justify-between text-slate-600'}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
