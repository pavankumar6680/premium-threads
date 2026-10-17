import type { ReactNode } from 'react';
import { formatMoney } from '../../lib/format';

export function TotalsCard({ subtotal, shipping, tax, total, freeShippingThreshold, children, currency = 'USD' }: {
  subtotal: number;
  shipping: number;
  tax: number;
  total: number;
  freeShippingThreshold?: number;
  currency?: string;
  children?: ReactNode;
}) {
  const remainingForFreeShipping = freeShippingThreshold !== undefined && shipping > 0 ? freeShippingThreshold - subtotal : 0;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs" aria-label="Order summary">
      <h2 className="text-lg font-semibold">Order summary</h2>
      <dl className="mt-4 space-y-2 text-sm">
        <Row label="Subtotal" value={formatMoney(subtotal, currency)} />
        <Row label="Shipping" value={shipping === 0 ? 'Free' : formatMoney(shipping, currency)} />
        <Row label="Estimated tax" value={formatMoney(tax, currency)} />
        <div className="flex justify-between border-t border-slate-200 pt-3 text-base font-bold">
          <dt>Total</dt>
          <dd>{formatMoney(total, currency)}</dd>
        </div>
      </dl>
      {remainingForFreeShipping > 0 && (
        <p className="mt-3 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-900">
          Add {formatMoney(remainingForFreeShipping, currency)} more for free shipping.
        </p>
      )}
      {children && <div className="mt-5">{children}</div>}
    </section>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between text-slate-700">
      <dt>{label}</dt>
      <dd className="font-medium text-slate-900">{value}</dd>
    </div>
  );
}
