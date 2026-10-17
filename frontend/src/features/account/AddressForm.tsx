import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { applyServerErrors } from '../auth/serverErrors';
import { addressSchema, emptyAddress, type AddressInput } from './addresses';

const FIELDS = ['fullName', 'line1', 'line2', 'city', 'state', 'postalCode', 'country', 'phone'] as const;

export function AddressForm({ initial = emptyAddress, submitLabel, onSubmit, onCancel, showDefaultToggle = true }: {
  initial?: AddressInput;
  submitLabel: string;
  onSubmit: (input: AddressInput) => Promise<unknown>;
  onCancel?: () => void;
  showDefaultToggle?: boolean;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(addressSchema), defaultValues: initial });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({ ...values, country: values.country.toUpperCase() });
    } catch (error) {
      setFormError(applyServerErrors(error, setError, FIELDS));
    }
  });

  return (
    <form onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
      {formError && <div className="sm:col-span-2"><Alert tone="error">{formError}</Alert></div>}
      <TextField className="sm:col-span-2" label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
      <TextField className="sm:col-span-2" label="Street address" autoComplete="address-line1" error={errors.line1?.message} {...register('line1')} />
      <TextField className="sm:col-span-2" label="Apartment, suite, etc. (optional)" autoComplete="address-line2" error={errors.line2?.message} {...register('line2')} />
      <TextField label="City" autoComplete="address-level2" error={errors.city?.message} {...register('city')} />
      <TextField label="State / region" autoComplete="address-level1" error={errors.state?.message} {...register('state')} />
      <TextField label="Postal code" autoComplete="postal-code" error={errors.postalCode?.message} {...register('postalCode')} />
      <TextField label="Country code" autoComplete="country" maxLength={2} error={errors.country?.message} {...register('country')} />
      <TextField className="sm:col-span-2" label="Phone (optional)" type="tel" autoComplete="tel" error={errors.phone?.message} {...register('phone')} />
      {showDefaultToggle && (
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" className="size-4 accent-brand-600" {...register('makeDefault')} />
          Use as my default address
        </label>
      )}
      <div className="flex gap-3 sm:col-span-2">
        <Button type="submit" isLoading={isSubmitting}>{submitLabel}</Button>
        {onCancel && <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  );
}
