import { AlertTriangle, ShoppingBag, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { buttonClasses } from '../../components/ui/Button';
import { QuantityStepper } from '../../components/ui/QuantityStepper';
import { PageSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { ApiError } from '../../lib/api';
import { formatMoney, pluralize } from '../../lib/format';
import { ProductImage } from '../catalog/components/ProductImage';
import { useCart, useRemoveCartItem, useUpdateCartItem, variantLabel, type CartItem } from './api';
import { TotalsCard } from './TotalsCard';

const MAX_PER_LINE = 99;

export function CartPage() {
  const { data: cart, isPending, error } = useCart();

  if (isPending) return <PageSpinner />;
  if (error) return <div className="mx-auto max-w-3xl px-4 py-10"><Alert tone="error">{error.message}</Alert></div>;

  if (cart.items.length === 0) {
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <ShoppingBag className="mx-auto size-12 text-slate-300" aria-hidden="true" />
        <h1 className="mt-4 text-2xl font-bold">Your cart is empty</h1>
        <p className="mt-2 text-slate-600">Browse the shop or start from a goal to build a complete bundle.</p>
        <Link to="/shop" className={buttonClasses('primary', 'lg', 'mt-6')}>Start shopping</Link>
      </div>
    );
  }

  const hasIssues = cart.items.some((i) => !i.available);

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Shopping cart</h1>
      <p className="mt-1 text-sm text-slate-600">{pluralize(cart.itemCount, 'item')}</p>

      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_340px]">
        <div>
          {hasIssues && (
            <div className="mb-4">
              <Alert tone="error">Some items are no longer available in the quantity you selected. Update them to continue.</Alert>
            </div>
          )}
          <ul className="divide-y divide-slate-200 rounded-2xl border border-slate-200 bg-white">
            {cart.items.map((item) => (
              <CartLine key={item.variantId} item={item} />
            ))}
          </ul>
          <Link to="/shop" className="mt-4 inline-block text-sm font-semibold text-brand-700 hover:underline">
            ← Continue shopping
          </Link>
        </div>

        <div className="lg:sticky lg:top-24 lg:self-start">
          <TotalsCard {...cart}>
            {cart.checkoutReady ? (
              <Link to="/checkout" className={buttonClasses('primary', 'lg', 'w-full')}>Proceed to checkout</Link>
            ) : (
              <button type="button" disabled className={buttonClasses('primary', 'lg', 'w-full')}>Proceed to checkout</button>
            )}
          </TotalsCard>
        </div>
      </div>
    </div>
  );
}

function CartLine({ item }: { item: CartItem }) {
  const update = useUpdateCartItem();
  const remove = useRemoveCartItem();
  const toast = useToast();
  const busy = update.isPending || remove.isPending;
  const onError = (error: Error) => toast.show(error instanceof ApiError ? error.message : 'Could not update your cart', 'error');

  return (
    <li className="flex gap-4 p-4">
      <Link to={`/products/${item.slug}`} className="w-20 shrink-0 sm:w-24" tabIndex={-1} aria-hidden="true">
        <ProductImage categorySlug={item.categorySlug} alt={item.name} size="sm" />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="text-xs uppercase tracking-wide text-slate-500">{item.brand}</p>
          <Link to={`/products/${item.slug}`} className="font-semibold hover:text-brand-700">{item.name}</Link>
          {variantLabel(item.size, item.color) && (
            <p className="mt-0.5 flex items-center gap-1.5 text-sm text-slate-700">
              {item.colorHex && <span className="size-3 rounded-full ring-1 ring-slate-300" style={{ backgroundColor: item.colorHex }} aria-hidden="true" />}
              {variantLabel(item.size, item.color)}
            </p>
          )}
          <p className="mt-0.5 text-sm text-slate-600">{formatMoney(item.unitPrice)} each</p>
          {!item.available && (
            <p className="mt-1 flex items-center gap-1 text-sm font-medium text-red-600">
              <AlertTriangle className="size-4" aria-hidden="true" />
              {item.availableQuantity === 0 ? 'Out of stock' : `Only ${item.availableQuantity} available`}
            </p>
          )}
        </div>
        <div className="flex items-center gap-3 sm:flex-col sm:items-end">
          <p className="font-bold">{formatMoney(item.lineTotal)}</p>
          <div className="flex items-center gap-2">
            <QuantityStepper
              label={`Quantity for ${item.name}`}
              value={item.quantity}
              max={Math.max(item.quantity, Math.min(item.availableQuantity, MAX_PER_LINE))}
              disabled={busy}
              onChange={(quantity) => update.mutate({ variantId: item.variantId, quantity }, { onError })}
            />
            <button
              type="button"
              onClick={() => remove.mutate(item.variantId, { onError })}
              disabled={busy}
              className="rounded-lg p-2 text-slate-500 hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
              aria-label={`Remove ${item.name}`}
            >
              <Trash2 className="size-4" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </li>
  );
}
