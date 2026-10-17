import { MapPin, Plus } from 'lucide-react';
import { useState } from 'react';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { useToast } from '../../components/ui/Toast';
import { ApiError } from '../../lib/api';
import { AddressForm } from './AddressForm';
import {
  formatAddress,
  useAddresses,
  useCreateAddress,
  useDeleteAddress,
  useSetDefaultAddress,
  useUpdateAddress,
  type Address,
} from './addresses';

export function AddressBookSection() {
  const { data: addresses, isPending, error } = useAddresses();
  const create = useCreateAddress();
  const update = useUpdateAddress();
  const remove = useDeleteAddress();
  const setDefault = useSetDefaultAddress();
  const toast = useToast();
  const [editing, setEditing] = useState<number | 'new' | null>(null);

  const onError = (e: Error) => toast.show(e instanceof ApiError ? e.message : 'Something went wrong', 'error');

  return (
    <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" aria-labelledby="addresses-heading">
      <div className="flex items-center justify-between gap-3">
        <h2 id="addresses-heading" className="flex items-center gap-2 text-lg font-semibold">
          <MapPin className="size-5 text-brand-600" aria-hidden="true" /> Addresses
        </h2>
        {editing === null && (
          <Button variant="secondary" size="sm" onClick={() => setEditing('new')}>
            <Plus className="size-4" aria-hidden="true" /> Add address
          </Button>
        )}
      </div>

      {error && <div className="mt-4"><Alert tone="error">{error.message}</Alert></div>}
      {isPending && <p className="mt-4 text-sm text-slate-500">Loading…</p>}

      {editing === 'new' && (
        <div className="mt-4 rounded-xl border border-slate-200 p-4">
          <AddressForm
            submitLabel="Save address"
            onCancel={() => setEditing(null)}
            onSubmit={async (input) => {
              await create.mutateAsync(input);
              setEditing(null);
              toast.show('Address saved');
            }}
          />
        </div>
      )}

      {addresses && addresses.length === 0 && editing !== 'new' && (
        <p className="mt-4 text-sm text-slate-600">You have no saved addresses yet.</p>
      )}

      <ul className="mt-4 space-y-3">
        {addresses?.map((address) =>
          editing === address.id ? (
            <li key={address.id} className="rounded-xl border border-slate-200 p-4">
              <AddressForm
                initial={toInput(address)}
                submitLabel="Update address"
                onCancel={() => setEditing(null)}
                onSubmit={async (input) => {
                  await update.mutateAsync({ id: address.id, input });
                  setEditing(null);
                  toast.show('Address updated');
                }}
              />
            </li>
          ) : (
            <li key={address.id} className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-slate-200 p-4 text-sm">
              <div>
                <p className="font-semibold">
                  {address.fullName}
                  {address.defaultAddress && <span className="ml-2 rounded bg-brand-50 px-1.5 py-0.5 text-xs font-medium text-brand-800">Default</span>}
                </p>
                <p className="text-slate-600">{formatAddress(address)}</p>
                {address.phone && <p className="text-slate-500">{address.phone}</p>}
              </div>
              <div className="flex gap-1">
                {!address.defaultAddress && (
                  <Button variant="ghost" size="sm" onClick={() => setDefault.mutate(address.id, { onError })}>Make default</Button>
                )}
                <Button variant="ghost" size="sm" onClick={() => setEditing(address.id)}>Edit</Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="!text-red-600 hover:!bg-red-50"
                  onClick={() => window.confirm('Delete this address?') && remove.mutate(address.id, { onError })}
                >
                  Delete
                </Button>
              </div>
            </li>
          ),
        )}
      </ul>
    </section>
  );
}

function toInput(a: Address) {
  return {
    fullName: a.fullName,
    line1: a.line1,
    line2: a.line2 ?? '',
    city: a.city,
    state: a.state,
    postalCode: a.postalCode,
    country: a.country,
    phone: a.phone ?? '',
    makeDefault: a.defaultAddress,
  };
}
