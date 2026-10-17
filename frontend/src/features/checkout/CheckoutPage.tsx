import clsx from 'clsx';
import { CreditCard, Lock, MapPin, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { ApiError } from '../../lib/api';
import { formatMoney } from '../../lib/format';
import { AddressForm } from '../account/AddressForm';
import { formatAddress, useAddresses, useCreateAddress } from '../account/addresses';
import { useCart } from '../cart/api';
import { TotalsCard } from '../cart/TotalsCard';
import { useCheckout } from '../orders/api';

function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();
  const addresses = useAddresses();
  const createAddress = useCreateAddress();
  const checkout = useCheckout();
  const [chosenAddressId, setChosenAddressId] = useState<number | null>(null);
  const [addingAddress, setAddingAddress] = useState(false);
  // One key per checkout attempt: retries and double clicks reuse it, so the server returns the same order.
  const [idempotencyKey] = useState(newIdempotencyKey);

  if (cart.isPending || addresses.isPending) return <PageSpinner />;
  if (cart.isError || addresses.isError) {
    return <div className="mx-auto max-w-3xl px-4 py-10"><Alert tone="error">We could not load your checkout. Please refresh.</Alert></div>;
  }
  if (cart.data.items.length === 0 && !checkout.isSuccess) return <Navigate to="/cart" replace />;

  const savedAddresses = addresses.data;
  const selectedId = chosenAddressId ?? savedAddresses.find((a) => a.defaultAddress)?.id ?? savedAddresses[0]?.id ?? null;
  const showForm = addingAddress || savedAddresses.length === 0;

  const placeOrder = () => {
    if (selectedId === null) return;
    checkout.mutate(
      { addressId: selectedId, idempotencyKey },
      { onSuccess: (order) => navigate(`/orders/${order.id}`, { replace: true, state: { placed: true } }) },
    );
  };

  const checkoutError = checkout.error instanceof ApiError ? checkout.error : null;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Checkout</h1>

      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="shipping-heading">
            <h2 id="shipping-heading" className="flex items-center gap-2 text-lg font-semibold">
              <MapPin className="size-5 text-brand-600" aria-hidden="true" /> Shipping address
            </h2>

            {savedAddresses.length > 0 && (
              <div className="mt-4 space-y-2" role="radiogroup" aria-label="Saved addresses">
                {savedAddresses.map((address) => (
                  <label
                    key={address.id}
                    className={clsx(
                      'flex cursor-pointer gap-3 rounded-xl border p-3 text-sm',
                      selectedId === address.id ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500' : 'border-slate-200 hover:border-slate-300',
                    )}
                  >
                    <input
                      type="radio"
                      name="address"
                      className="mt-0.5 size-4 accent-brand-600"
                      checked={selectedId === address.id}
                      onChange={() => setChosenAddressId(address.id)}
                    />
                    <span>
                      <span className="font-semibold">{address.fullName}</span>
                      {address.defaultAddress && <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">Default</span>}
                      <span className="block text-slate-600">{formatAddress(address)}</span>
                    </span>
                  </label>
                ))}
              </div>
            )}

            {showForm ? (
              <div className="mt-4">
                <AddressForm
                  submitLabel="Save address"
                  showDefaultToggle={savedAddresses.length > 0}
                  onCancel={savedAddresses.length > 0 ? () => setAddingAddress(false) : undefined}
                  onSubmit={async (input) => {
                    const created = await createAddress.mutateAsync(input);
                    setChosenAddressId(created.id);
                    setAddingAddress(false);
                  }}
                />
              </div>
            ) : (
              <Button variant="ghost" size="sm" className="mt-3" onClick={() => setAddingAddress(true)}>
                <Plus className="size-4" aria-hidden="true" /> Add a new address
              </Button>
            )}
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="payment-heading">
            <h2 id="payment-heading" className="flex items-center gap-2 text-lg font-semibold">
              <CreditCard className="size-5 text-brand-600" aria-hidden="true" /> Payment
            </h2>
            <div className="mt-3">
              <Alert tone="info">
                This is a demo store. Payment is <strong>simulated</strong>: no card details are collected and nothing is charged.
              </Alert>
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="items-heading">
            <h2 id="items-heading" className="text-lg font-semibold">Items</h2>
            <ul className="mt-3 divide-y divide-slate-100 text-sm">
              {cart.data.items.map((item) => (
                <li key={item.productId} className="flex justify-between gap-4 py-2">
                  <span>
                    {item.name} <span className="text-slate-500">× {item.quantity}</span>
                  </span>
                  <span className="font-medium">{formatMoney(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <div className="lg:sticky lg:top-24 lg:self-start">
          <TotalsCard {...cart.data}>
            {checkoutError && (
              <div className="mb-3">
                <Alert tone="error">
                  {checkoutError.message}{' '}
                  {(checkoutError.code === 'INSUFFICIENT_STOCK' || checkoutError.code === 'PRODUCT_UNAVAILABLE') && (
                    <Link to="/cart" className="font-semibold underline">Review your cart</Link>
                  )}
                </Alert>
              </div>
            )}
            {!cart.data.checkoutReady && (
              <div className="mb-3">
                <Alert tone="error">Some items are unavailable. <Link to="/cart" className="font-semibold underline">Review your cart</Link></Alert>
              </div>
            )}
            <Button
              size="lg"
              className="w-full"
              onClick={placeOrder}
              isLoading={checkout.isPending}
              disabled={selectedId === null || showForm || !cart.data.checkoutReady}
            >
              <Lock className="size-4" aria-hidden="true" /> Place order · {formatMoney(cart.data.total)}
            </Button>
            <p className="mt-2 text-center text-xs text-slate-500">Final prices are confirmed by the server when you place the order.</p>
          </TotalsCard>
        </div>
      </div>
    </div>
  );
}
