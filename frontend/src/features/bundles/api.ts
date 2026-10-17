import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { useToast } from '../../components/ui/Toast';
import { api, ApiError } from '../../lib/api';
import { useCurrentUser } from '../auth/hooks';
import type { Bundle, Tier } from '../recommendations/api';

export type Alternative = {
  productId: number;
  slug: string;
  name: string;
  brand: string;
  price: number;
  priceDelta: number;
  ratingAvg: number;
  ratingCount: number;
  availableQuantity: number;
  inStock: boolean;
  compatible: boolean;
  issues: string[];
  current: boolean;
};

export type AccessorySuggestion = {
  categorySlug: string;
  categoryName: string;
  reason: string;
  products: Alternative[];
};

export type BundleSummary = {
  id: string;
  name: string;
  tier: Tier;
  status: 'DRAFT' | 'SAVED';
  goalId: string;
  goalTitle: string;
  budget: number;
  totalPrice: number;
  itemCount: number;
  updatedAt: string;
};

export type Strategy = 'BUDGET' | 'BALANCED' | 'PREMIUM';

export const bundleKeys = {
  bundle: (id: string) => ['bundles', id] as const,
  alternatives: (id: string, category: string) => ['bundles', id, 'alternatives', category] as const,
  accessories: (id: string) => ['bundles', id, 'accessories'] as const,
  mine: ['bundles', 'mine'] as const,
};

export function useBundle(id: string) {
  return useQuery({ queryKey: bundleKeys.bundle(id), queryFn: () => api.get<Bundle>(`/bundles/${id}`) });
}

export function useMyBundles() {
  const { data: user } = useCurrentUser();
  return useQuery({ queryKey: bundleKeys.mine, queryFn: () => api.get<BundleSummary[]>('/me/bundles'), enabled: !!user });
}

export function useAlternatives(id: string, category: string | null) {
  return useQuery({
    queryKey: bundleKeys.alternatives(id, category ?? ''),
    queryFn: () => api.get<Alternative[]>(`/bundles/${id}/alternatives?category=${encodeURIComponent(category!)}`),
    enabled: !!category,
  });
}

export function useAccessories(id: string, enabled: boolean) {
  return useQuery({
    queryKey: bundleKeys.accessories(id),
    queryFn: () => api.get<AccessorySuggestion[]>(`/bundles/${id}/accessories`),
    enabled,
  });
}

/** Creates the customer's editable copy and opens it; anonymous shoppers sign in first. */
export function useCustomizeBundle() {
  const { data: user } = useCurrentUser();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const mutation = useMutation({
    mutationFn: (bundleId: string) => api.post<Bundle>(`/bundles/${bundleId}/customize`),
    onSuccess: (copy) => {
      queryClient.setQueryData(bundleKeys.bundle(copy.id), copy);
      void queryClient.invalidateQueries({ queryKey: bundleKeys.mine });
      navigate(`/bundles/${copy.id}`);
    },
    onError: (error) => toast.show(error instanceof ApiError ? error.message : 'Could not customize the bundle', 'error'),
  });
  const customize = (bundleId: string) => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(window.location.pathname)}`);
      return;
    }
    mutation.mutate(bundleId);
  };
  return { customize, isPending: mutation.isPending, pendingId: mutation.variables };
}

/**
 * Every workspace edit returns the updated bundle: it replaces the cached bundle, and the alternatives and
 * accessories (which depend on the rest of the bundle) are refetched.
 */
export function useBundleEdits(bundleId: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const onSuccess = (bundle: Bundle) => {
    queryClient.setQueryData(bundleKeys.bundle(bundleId), bundle);
    void queryClient.invalidateQueries({ queryKey: ['bundles', bundleId, 'alternatives'] });
    void queryClient.invalidateQueries({ queryKey: bundleKeys.accessories(bundleId) });
    void queryClient.invalidateQueries({ queryKey: bundleKeys.mine });
  };
  const onError = (error: Error) => toast.show(error instanceof ApiError ? error.message : 'Could not update the bundle', 'error');
  const edit = <T,>(fn: (vars: T) => Promise<Bundle>) => useMutation({ mutationFn: fn, onSuccess, onError });

  return {
    replace: edit(({ category, productId }: { category: string; productId: number }) =>
      api.put<Bundle>(`/bundles/${bundleId}/items/${category}`, { productId })),
    update: edit(({ category, quantity, locked }: { category: string; quantity?: number; locked?: boolean }) =>
      api.patch<Bundle>(`/bundles/${bundleId}/items/${category}`, { quantity, locked })),
    remove: edit((category: string) => api.delete<Bundle>(`/bundles/${bundleId}/items/${category}`)),
    add: edit((productId: number) => api.post<Bundle>(`/bundles/${bundleId}/items`, { productId })),
    budget: edit((budget: number) => api.put<Bundle>(`/bundles/${bundleId}/budget`, { budget })),
    save: edit((name: string) => api.post<Bundle>(`/bundles/${bundleId}/save`, { name })),
    // re-optimize reports "nothing fits" in the page itself, so it has no toast
    reoptimize: useMutation({
      mutationFn: (strategy: Strategy | null) =>
        api.post<Bundle>(`/bundles/${bundleId}/reoptimize${strategy ? `?strategy=${strategy}` : ''}`),
      onSuccess,
    }),
  };
}

export function useDeleteBundle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bundleId: string) => api.delete<void>(`/bundles/${bundleId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: bundleKeys.mine }),
  });
}

export const strategyLabels: Record<Strategy, string> = {
  BUDGET: 'Lowest cost',
  BALANCED: 'Best value',
  PREMIUM: 'Best quality',
};
