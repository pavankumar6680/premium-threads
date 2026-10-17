import clsx from 'clsx';
import { CircleAlert, CircleCheck, ShieldCheck, ShoppingCart, Star, Wand2 } from 'lucide-react';
import { Link } from 'react-router';
import { Button } from '../../components/ui/Button';
import { formatMoney } from '../../lib/format';
import { ProductImage } from '../catalog/components/ProductImage';
import { useCustomizeBundle } from '../bundles/api';
import { tierLabels, useAddBundleToCart, type Bundle } from './api';

export function BundleCard({ bundle, recommended }: { bundle: Bundle; recommended: boolean }) {
  const { addBundle, isPending, pendingId } = useAddBundleToCart();
  const { customize, isPending: customizing, pendingId: customizingId } = useCustomizeBundle();
  const share = Math.min(100, Math.round((bundle.currentTotal / bundle.budget) * 100));
  const remaining = bundle.budget - bundle.currentTotal;
  const priceChanged = Math.abs(bundle.currentTotal - bundle.totalPrice) >= 0.01;

  return (
    <article
      aria-labelledby={`bundle-${bundle.id}`}
      className={clsx(
        'flex flex-col rounded-2xl border bg-white shadow-xs',
        recommended ? 'border-brand-500 ring-2 ring-brand-500' : 'border-slate-200',
      )}
    >
      <header className="border-b border-slate-100 p-5">
        <div className="flex items-center justify-between gap-2">
          <h2 id={`bundle-${bundle.id}`} className="text-lg font-bold">{bundle.name}</h2>
          {recommended && (
            <span className="inline-flex items-center gap-1 rounded-full bg-brand-600 px-2.5 py-0.5 text-xs font-semibold text-white">
              <Star className="size-3" fill="currentColor" aria-hidden="true" /> Your preference
            </span>
          )}
        </div>
        <p className="mt-3 text-3xl font-extrabold tracking-tight">{formatMoney(bundle.currentTotal)}</p>
        {priceChanged && (
          <p className="text-xs text-amber-700">Prices changed since this was recommended ({formatMoney(bundle.totalPrice)}).</p>
        )}
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
          <div className={clsx('h-full rounded-full', remaining >= 0 ? 'bg-brand-600' : 'bg-red-500')} style={{ width: `${share}%` }} />
        </div>
        <p className="mt-1.5 text-xs text-slate-600">
          {share}% of your {formatMoney(bundle.budget)} budget ·{' '}
          {remaining >= 0 ? `${formatMoney(remaining)} left` : `${formatMoney(-remaining)} over`}
        </p>
        {bundle.summary && <p className="mt-3 text-sm text-slate-700">{bundle.summary}</p>}
      </header>

      <ul className="flex-1 divide-y divide-slate-100" aria-label={`${tierLabels[bundle.tier]} bundle items`}>
        {bundle.items.map((item) => (
          <li key={item.productId} className="flex gap-3 p-4">
            <div className="w-14 shrink-0">
              <ProductImage categorySlug={item.categorySlug} alt={item.name} size="sm" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-xs uppercase tracking-wide text-slate-500">
                    {item.categoryName}
                    {item.optional && <span className="ml-1.5 rounded bg-slate-100 px-1.5 py-0.5 normal-case tracking-normal text-slate-600">nice to have</span>}
                  </p>
                  {item.slug ? (
                    <Link to={`/products/${item.slug}`} className="text-sm font-semibold hover:text-brand-700">{item.name}</Link>
                  ) : (
                    <p className="text-sm font-semibold">{item.name}</p>
                  )}
                </div>
                <p className="shrink-0 text-sm font-semibold">
                  {item.quantity > 1 && <span className="font-normal text-slate-500">{item.quantity} × </span>}
                  {formatMoney(item.currentPrice ?? item.unitPrice)}
                </p>
              </div>
              {item.explanation && <p className="mt-1 text-xs leading-relaxed text-slate-600">{item.explanation}</p>}
              {!item.available && <p className="mt-1 text-xs font-semibold text-red-600">No longer available in this quantity</p>}
            </div>
          </li>
        ))}
      </ul>

      <footer className="space-y-3 border-t border-slate-100 p-5">
        <CompatibilitySummary bundle={bundle} />
        <Button
          className="w-full"
          disabled={!bundle.purchasable}
          isLoading={isPending && pendingId === bundle.id}
          onClick={() => addBundle(bundle.id)}
        >
          <ShoppingCart className="size-4" aria-hidden="true" /> Add bundle to cart
        </Button>
        <Button
          variant="secondary"
          className="w-full"
          isLoading={customizing && customizingId === bundle.id}
          onClick={() => customize(bundle.id)}
          aria-label={`Customize the ${bundle.name.toLowerCase()}`}
        >
          <Wand2 className="size-4" aria-hidden="true" /> Customize
        </Button>
      </footer>
    </article>
  );
}

function CompatibilitySummary({ bundle }: { bundle: Bundle }) {
  const { compatibility } = bundle;
  const names = new Map(bundle.items.map((i) => [i.productId, i.name]));
  if (!compatibility.compatible || compatibility.violations.length > 0) {
    return (
      <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">
        <p className="flex items-center gap-1.5 font-semibold"><CircleAlert className="size-4" aria-hidden="true" /> Compatibility issues</p>
        <ul className="mt-1 list-disc pl-5">
          {compatibility.violations.map((v, i) => <li key={i}>{v.message}</li>)}
        </ul>
      </div>
    );
  }
  if (compatibility.passed.length === 0) {
    return (
      <p className="flex items-center gap-1.5 text-sm text-slate-600">
        <ShieldCheck className="size-4 text-emerald-600" aria-hidden="true" /> No compatibility constraints between these items
      </p>
    );
  }
  return (
    <details className="text-sm">
      <summary className="flex cursor-pointer list-none items-center gap-1.5 font-medium text-emerald-700">
        <CircleCheck className="size-4" aria-hidden="true" />
        {compatibility.passed.length} compatibility {compatibility.passed.length === 1 ? 'check' : 'checks'} passed
      </summary>
      <ul className="mt-2 space-y-1 pl-6 text-xs text-slate-600">
        {compatibility.passed.map((c, i) => (
          <li key={i}>{c.productIds.map((id) => names.get(id)).join(' + ')}: {c.label}</li>
        ))}
      </ul>
    </details>
  );
}
