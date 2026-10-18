import clsx from 'clsx';
import { ArrowLeftRight, CircleAlert, CircleCheck, Lock, LockOpen, Plus, RefreshCw, Save, ShoppingCart, Trash2, Wand2, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button, buttonClasses } from '../../components/ui/Button';
import { QuantityStepper } from '../../components/ui/QuantityStepper';
import { PageSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { ApiError } from '../../lib/api';
import { formatMoney } from '../../lib/format';
import { ProductImage } from '../catalog/components/ProductImage';
import { Rating } from '../catalog/components/Rating';
import { tierLabels, useAddBundleToCart, type Bundle, type BundleItem } from '../recommendations/api';
import {
  strategyLabels,
  useAccessories,
  useAlternatives,
  useBundle,
  useBundleEdits,
  useCustomizeBundle,
  useDeleteBundle,
  type Alternative,
  type Strategy,
} from './api';
import { ItemVariantLabel, ItemVariantSelect } from './ItemVariant';

export function BundleWorkspacePage() {
  const { id = '' } = useParams();
  const { data: bundle, isPending, error } = useBundle(id);

  if (isPending) return <PageSpinner />;
  if (error) {
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="text-2xl font-bold">Bundle not found</h1>
        <Link to="/bundles" className={buttonClasses('primary', 'md', 'mt-6')}>My bundles</Link>
      </div>
    );
  }
  return <Workspace bundle={bundle} />;
}

function Workspace({ bundle }: { bundle: Bundle }) {
  const [swapping, setSwapping] = useState<string | null>(null);
  const edits = useBundleEdits(bundle.id);
  const { customize, isPending: customizing } = useCustomizeBundle();
  const editable = bundle.editable;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <Link to={`/goals/${bundle.goalId}`} className="text-sm font-semibold text-brand-700 hover:underline">← {bundle.goalTitle}</Link>
      <div className="mt-1 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{bundle.name}</h1>
        <StatusBadge status={bundle.status} />
      </div>
      <p className="mt-1 text-sm text-slate-600">
        Based on the {tierLabels[bundle.tier].toLowerCase()} recommendation{editable && ' · changes are saved automatically to this draft'}
      </p>

      {!editable && (
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-brand-200 bg-brand-50 p-4">
          <p className="text-sm text-brand-900">
            {bundle.status === 'RECOMMENDED'
              ? 'This is a recommended bundle. Customize it to swap products, lock favorites and change the budget.'
              : 'This bundle belongs to someone else. Make your own copy to change it.'}
          </p>
          <Button onClick={() => customize(bundle.id)} isLoading={customizing}>
            <Wand2 className="size-4" aria-hidden="true" /> Customize this bundle
          </Button>
        </div>
      )}

      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          <ul className="space-y-3" aria-label="Bundle items">
            {bundle.items.map((item) => (
              <ItemRow
                key={item.categorySlug}
                bundle={bundle}
                item={item}
                swapping={swapping === item.categorySlug}
                onToggleSwap={() => setSwapping((s) => (s === item.categorySlug ? null : item.categorySlug))}
                edits={edits}
              />
            ))}
          </ul>
          {editable && <Accessories bundle={bundle} edits={edits} />}
        </div>

        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start" aria-label="Bundle summary">
          <BudgetPanel key={bundle.budget} bundle={bundle} edits={edits} />
          <CompatibilityPanel bundle={bundle} />
          {editable && <ReoptimizePanel bundle={bundle} edits={edits} />}
          <ActionsPanel bundle={bundle} edits={edits} />
        </aside>
      </div>
    </div>
  );
}

type Edits = ReturnType<typeof useBundleEdits>;

function StatusBadge({ status }: { status: Bundle['status'] }) {
  const styles = { RECOMMENDED: 'bg-slate-100 text-slate-700', DRAFT: 'bg-amber-100 text-amber-800', SAVED: 'bg-emerald-100 text-emerald-800' };
  const labels = { RECOMMENDED: 'Recommended', DRAFT: 'Draft', SAVED: 'Saved' };
  return <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-semibold', styles[status])}>{labels[status]}</span>;
}

function ItemRow({ bundle, item, swapping, onToggleSwap, edits }: {
  bundle: Bundle;
  item: BundleItem;
  swapping: boolean;
  onToggleSwap: () => void;
  edits: Edits;
}) {
  const busy = edits.update.isPending || edits.remove.isPending || edits.replace.isPending;
  return (
    <li className={clsx('rounded-2xl border bg-white', item.available ? 'border-slate-200' : 'border-red-300')}>
      <div className="flex gap-4 p-4">
        <div className="w-16 shrink-0 sm:w-20">
          <ProductImage categorySlug={item.categorySlug} colorHex={item.variant?.colorHex} alt={item.name} size="sm" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wide text-slate-500">
                {item.categoryName}
                {item.optional && <span className="ml-1.5 rounded bg-slate-100 px-1.5 py-0.5 normal-case tracking-normal text-slate-600">nice to have</span>}
                {item.locked && <span className="ml-1.5 inline-flex items-center gap-0.5 rounded bg-brand-100 px-1.5 py-0.5 normal-case tracking-normal text-brand-800"><Lock className="size-3" aria-hidden="true" />locked</span>}
              </p>
              {item.slug ? (
                <Link to={`/products/${item.slug}`} className="font-semibold hover:text-brand-700">{item.name}</Link>
              ) : (
                <p className="font-semibold">{item.name}</p>
              )}
              {item.brand && <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{item.brand}</p>}
              <ItemVariantLabel item={item} />
              <div className="mt-0.5"><Rating value={item.ratingAvg} count={item.ratingCount} /></div>
            </div>
            <p className="font-bold">{formatMoney(item.lineTotal)}</p>
          </div>
          {item.explanation && <p className="mt-1.5 text-xs leading-relaxed text-slate-600">{item.explanation}</p>}
          {!item.available && <p className="mt-1 text-xs font-semibold text-red-600">Not available in this quantity: swap it for another product.</p>}

          {bundle.editable && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <ItemVariantSelect
                item={item}
                disabled={busy}
                onChoose={(variantId) => edits.update.mutate({ category: item.categorySlug, variantId })}
              />
              <QuantityStepper
                label={`Quantity of ${item.categoryName}`}
                value={item.quantity}
                max={10}
                disabled={busy}
                onChange={(quantity) => edits.update.mutate({ category: item.categorySlug, quantity })}
              />
              <Button
                variant="secondary"
                size="sm"
                aria-pressed={item.locked}
                onClick={() => edits.update.mutate({ category: item.categorySlug, locked: !item.locked })}
                disabled={busy}
              >
                {item.locked ? <LockOpen className="size-4" aria-hidden="true" /> : <Lock className="size-4" aria-hidden="true" />}
                {item.locked ? 'Unlock' : 'Lock'}
              </Button>
              <Button variant={swapping ? 'primary' : 'secondary'} size="sm" onClick={onToggleSwap} aria-expanded={swapping}>
                <ArrowLeftRight className="size-4" aria-hidden="true" /> Swap
              </Button>
              {item.optional && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="!text-red-600 hover:!bg-red-50"
                  onClick={() => edits.remove.mutate(item.categorySlug)}
                  disabled={busy}
                  aria-label={`Remove ${item.categoryName}`}
                >
                  <Trash2 className="size-4" aria-hidden="true" /> Remove
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
      {swapping && (
        <AlternativesPanel
          bundleId={bundle.id}
          item={item}
          onChoose={(productId) => edits.replace.mutate({ category: item.categorySlug, productId }, { onSuccess: onToggleSwap })}
          onClose={onToggleSwap}
          choosing={edits.replace.isPending}
        />
      )}
    </li>
  );
}

function AlternativesPanel({ bundleId, item, onChoose, onClose, choosing }: {
  bundleId: string;
  item: BundleItem;
  onChoose: (productId: number) => void;
  onClose: () => void;
  choosing: boolean;
}) {
  const { data, isPending } = useAlternatives(bundleId, item.categorySlug);
  return (
    <section className="border-t border-slate-100 bg-slate-50 p-4" aria-label={`Alternatives for ${item.categoryName}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">Other {item.categoryName.toLowerCase()}</h3>
        <button type="button" onClick={onClose} className="rounded p-1 text-slate-500 hover:bg-slate-200" aria-label="Close alternatives">
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>
      {isPending && <p className="mt-2 text-sm text-slate-500">Loading…</p>}
      <ul className="mt-2 max-h-96 space-y-2 overflow-y-auto">
        {data?.map((alt) => (
          <AlternativeRow key={alt.productId} alt={alt} onChoose={() => onChoose(alt.productId)} disabled={choosing} />
        ))}
      </ul>
    </section>
  );
}

function AlternativeRow({ alt, onChoose, disabled }: { alt: Alternative; onChoose: () => void; disabled: boolean }) {
  return (
    <li className={clsx('rounded-xl border bg-white p-3', alt.current ? 'border-brand-400' : 'border-slate-200')}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-semibold">{alt.name}</p>
          <div className="flex flex-wrap items-center gap-2">
            <Rating value={alt.ratingAvg} count={alt.ratingCount} />
            {!alt.inStock && <span className="text-xs font-semibold text-red-600">Out of stock</span>}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">{formatMoney(alt.price)}</span>
          {!alt.current && <PriceDelta delta={alt.priceDelta} />}
          {alt.current ? (
            <span className="rounded-full bg-brand-100 px-2.5 py-1 text-xs font-semibold text-brand-800">Current</span>
          ) : (
            <Button size="sm" variant={alt.compatible ? 'primary' : 'secondary'} onClick={onChoose} disabled={disabled || !alt.inStock}>
              Choose
            </Button>
          )}
        </div>
      </div>
      {alt.compatible ? (
        <p className="mt-1 flex items-center gap-1 text-xs text-emerald-700"><CircleCheck className="size-3.5" aria-hidden="true" /> Compatible with the rest of your bundle</p>
      ) : (
        <ul className="mt-1 space-y-0.5 text-xs text-red-700">
          {alt.issues.map((issue) => (
            <li key={issue} className="flex items-center gap-1"><CircleAlert className="size-3.5 shrink-0" aria-hidden="true" />{issue}</li>
          ))}
        </ul>
      )}
    </li>
  );
}

function PriceDelta({ delta }: { delta: number }) {
  if (Math.abs(delta) < 0.005) return <span className="text-xs text-slate-500">same price</span>;
  return (
    <span className={clsx('rounded px-1.5 py-0.5 text-xs font-semibold', delta > 0 ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-700')}>
      {delta > 0 ? '+' : '−'}{formatMoney(Math.abs(delta))}
    </span>
  );
}

function Accessories({ bundle, edits }: { bundle: Bundle; edits: Edits }) {
  const { data } = useAccessories(bundle.id, true);
  if (!data || data.length === 0) return null;
  return (
    <section aria-labelledby="extras-heading">
      <h2 id="extras-heading" className="text-lg font-semibold">Add compatible extras</h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        {data.map((s) => (
          <div key={s.categorySlug} className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="font-semibold">{s.categoryName}</p>
            <p className="text-xs text-slate-500">{s.reason}</p>
            <ul className="mt-2 space-y-2">
              {s.products.map((p) => (
                <li key={p.productId} className="flex items-center justify-between gap-2 text-sm">
                  <span className="min-w-0 truncate">{p.name}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className="font-semibold">{formatMoney(p.price)}</span>
                    <Button size="sm" variant="secondary" onClick={() => edits.add.mutate(p.productId)} disabled={edits.add.isPending} aria-label={`Add ${p.name}`}>
                      <Plus className="size-4" aria-hidden="true" />
                    </Button>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}

function BudgetPanel({ bundle, edits }: { bundle: Bundle; edits: Edits }) {
  const [draft, setDraft] = useState(String(bundle.budget));
  const remaining = bundle.budget - bundle.currentTotal;
  const share = Math.min(100, Math.round((bundle.currentTotal / bundle.budget) * 100));
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const value = Number(draft);
    if (value >= 1 && value !== bundle.budget) edits.budget.mutate(value);
  };
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-label="Budget">
      <p className="text-sm text-slate-500">Total</p>
      <p className="text-3xl font-extrabold tracking-tight">{formatMoney(bundle.currentTotal)}</p>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
        <div className={clsx('h-full rounded-full', remaining >= 0 ? 'bg-brand-600' : 'bg-red-500')} style={{ width: `${share}%` }} />
      </div>
      <p className={clsx('mt-1.5 text-sm font-medium', remaining >= 0 ? 'text-slate-700' : 'text-red-600')} aria-live="polite">
        {remaining >= 0 ? `${formatMoney(remaining)} left of ${formatMoney(bundle.budget)}` : `${formatMoney(-remaining)} over your ${formatMoney(bundle.budget)} budget`}
      </p>
      {bundle.editable && (
        <form onSubmit={submit} className="mt-3 flex items-end gap-2">
          <label className="flex-1 text-xs font-medium text-slate-600">
            Budget (USD)
            <input
              type="number"
              min={1}
              step="0.01"
              inputMode="decimal"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              className="mt-1 block h-9 w-full rounded-lg border border-slate-300 px-2 text-sm text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
            />
          </label>
          <Button type="submit" variant="secondary" size="sm" isLoading={edits.budget.isPending}>Update</Button>
        </form>
      )}
    </section>
  );
}

function CompatibilityPanel({ bundle }: { bundle: Bundle }) {
  const { compatibility } = bundle;
  const names = new Map(bundle.items.map((i) => [i.productId, i.name]));
  return (
    <section className={clsx('rounded-2xl border p-5 text-sm', compatibility.compatible ? 'border-emerald-200 bg-emerald-50' : 'border-red-200 bg-red-50')} aria-label="Compatibility" aria-live="polite">
      <p className={clsx('flex items-center gap-1.5 font-semibold', compatibility.compatible ? 'text-emerald-800' : 'text-red-800')}>
        {compatibility.compatible ? <CircleCheck className="size-4" aria-hidden="true" /> : <CircleAlert className="size-4" aria-hidden="true" />}
        {compatibility.compatible ? 'Everything works together' : 'Compatibility conflicts'}
      </p>
      <ul className="mt-2 space-y-1 text-xs">
        {compatibility.violations.map((v, i) => <li key={`v${i}`} className="text-red-700">✗ {v.message}</li>)}
        {compatibility.passed.map((c, i) => (
          <li key={`p${i}`} className="text-slate-700">✓ {c.productIds.map((id) => names.get(id)).join(' + ')}: {c.label}</li>
        ))}
        {compatibility.passed.length === 0 && compatibility.violations.length === 0 && (
          <li className="text-slate-600">No rules apply between these items.</li>
        )}
      </ul>
    </section>
  );
}

function ReoptimizePanel({ bundle, edits }: { bundle: Bundle; edits: Edits }) {
  const [strategy, setStrategy] = useState<Strategy | ''>('');
  const error = edits.reoptimize.error instanceof ApiError ? edits.reoptimize.error : null;
  const suggestions = (error?.details.suggestions as string[] | undefined) ?? [];
  const lockedCount = bundle.items.filter((i) => i.locked).length;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-label="Re-optimize">
      <h2 className="flex items-center gap-1.5 font-semibold"><RefreshCw className="size-4 text-brand-600" aria-hidden="true" /> Re-optimize</h2>
      <p className="mt-1 text-xs text-slate-600">
        Finds the best compatible products for every unlocked item within your budget.{' '}
        {lockedCount > 0 ? `${lockedCount} locked ${lockedCount === 1 ? 'item stays' : 'items stay'}.` : 'Lock items you want to keep.'}
      </p>
      <label className="mt-3 block text-xs font-medium text-slate-600">
        Optimize for
        <select
          value={strategy}
          onChange={(e) => setStrategy(e.target.value as Strategy | '')}
          className="mt-1 block h-9 w-full rounded-lg border border-slate-300 bg-white px-2 text-sm text-slate-900"
        >
          <option value="">{tierLabels[bundle.tier]} (as recommended)</option>
          {(Object.keys(strategyLabels) as Strategy[]).map((s) => <option key={s} value={s}>{strategyLabels[s]}</option>)}
        </select>
      </label>
      <Button className="mt-3 w-full" variant="secondary" isLoading={edits.reoptimize.isPending}
        onClick={() => edits.reoptimize.mutate(strategy || null)}>
        Re-optimize unlocked items
      </Button>
      {error && (
        <div className="mt-3">
          <Alert tone="error">
            {error.message}
            {suggestions.length > 0 && <ul className="mt-1 list-disc pl-4">{suggestions.map((s) => <li key={s}>{s}</li>)}</ul>}
          </Alert>
        </div>
      )}
    </section>
  );
}

function ActionsPanel({ bundle, edits }: { bundle: Bundle; edits: Edits }) {
  const [name, setName] = useState(bundle.status === 'SAVED' ? bundle.name : `${bundle.goalTitle} · ${tierLabels[bundle.tier]}`);
  const { addBundle, isPending: adding } = useAddBundleToCart();
  const remove = useDeleteBundle();
  const navigate = useNavigate();
  const toast = useToast();
  const save = (e: FormEvent) => {
    e.preventDefault();
    if (name.trim()) edits.save.mutate(name.trim(), { onSuccess: () => toast.show('Bundle saved') });
  };
  return (
    <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5" aria-label="Actions">
      {bundle.editable && (
        <form onSubmit={save} className="space-y-2">
          <label className="block text-xs font-medium text-slate-600">
            Bundle name
            <input
              value={name}
              maxLength={120}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 block h-9 w-full rounded-lg border border-slate-300 px-2 text-sm text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
            />
          </label>
          <Button type="submit" variant="secondary" className="w-full" isLoading={edits.save.isPending}>
            <Save className="size-4" aria-hidden="true" /> {bundle.status === 'SAVED' ? 'Save changes' : 'Save bundle'}
          </Button>
        </form>
      )}
      <Button className="w-full" size="lg" disabled={!bundle.purchasable} isLoading={adding} onClick={() => addBundle(bundle.id)}>
        <ShoppingCart className="size-4" aria-hidden="true" /> Add bundle to cart
      </Button>
      {!bundle.purchasable && (
        <p className="text-xs text-red-600">
          {bundle.items.some((i) => i.needsSize)
            ? 'Choose a size for every piece to buy this outfit.'
            : 'Resolve compatibility conflicts and unavailable items to buy this bundle.'}
        </p>
      )}
      {bundle.editable && (
        <button
          type="button"
          className="w-full text-center text-xs font-semibold text-slate-500 hover:text-red-600"
          onClick={() =>
            window.confirm('Delete this bundle?') &&
            remove.mutate(bundle.id, { onSuccess: () => navigate('/bundles', { replace: true }) })
          }
        >
          Delete bundle
        </button>
      )}
    </section>
  );
}
