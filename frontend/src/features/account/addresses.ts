import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { api } from '../../lib/api';

export type Address = {
  id: number;
  fullName: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  phone: string | null;
  defaultAddress: boolean;
};

// Mirrors AddressController.AddressRequest validation.
export const addressSchema = z.object({
  fullName: z.string().trim().min(1, 'Name is required').max(120),
  line1: z.string().trim().min(1, 'Street address is required').max(200),
  line2: z.string().trim().max(200),
  city: z.string().trim().min(1, 'City is required').max(100),
  state: z.string().trim().min(1, 'State or region is required').max(100),
  postalCode: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9][A-Za-z0-9 -]{1,9}$/, 'Enter a valid postal code'),
  country: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{2}$/, 'Use a 2-letter country code, e.g. US'),
  phone: z
    .string()
    .trim()
    .regex(/^$|^[+0-9 ()-]{6,30}$/, 'Enter a valid phone number'),
  makeDefault: z.boolean(),
});

export type AddressInput = z.infer<typeof addressSchema>;

export const emptyAddress: AddressInput = {
  fullName: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'US',
  phone: '',
  makeDefault: false,
};

export const addressKey = ['addresses'] as const;

export function useAddresses() {
  return useQuery({ queryKey: addressKey, queryFn: () => api.get<Address[]>('/me/addresses') });
}

function useAddressMutation<TVars, TResult>(mutationFn: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: addressKey }),
  });
}

export const useCreateAddress = () =>
  useAddressMutation((input: AddressInput) => api.post<Address>('/me/addresses', input));

export const useUpdateAddress = () =>
  useAddressMutation(({ id, input }: { id: number; input: AddressInput }) =>
    api.put<Address>(`/me/addresses/${id}`, input),
  );

export const useDeleteAddress = () => useAddressMutation((id: number) => api.delete<void>(`/me/addresses/${id}`));

export const useSetDefaultAddress = () =>
  useAddressMutation((id: number) => api.post<Address>(`/me/addresses/${id}/default`));

export function formatAddress(a: Address): string {
  return [a.line1, a.line2, `${a.city}, ${a.state} ${a.postalCode}`, a.country].filter(Boolean).join(', ');
}
