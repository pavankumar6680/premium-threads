import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router';
import { useToast } from '../../components/ui/Toast';
import { api, ApiError } from '../../lib/api';
import { useCurrentUser } from '../auth/hooks';
import { cartKey, type Cart } from '../cart/api';

export type Tier = 'BUDGET' | 'BALANCED' | 'PREMIUM' | 'CUSTOM';

export type CompatibilityReport = {
  compatible: boolean;
  passed: { ruleCode: string; label: string; productIds: number[] }[];
  violations: { ruleCode: string; severity: 'BLOCK' | 'WARN'; message: string; productIds: number[] }[];
};

export type BundleItem = {
  productId: number;
  slug: string | null;
  sku: string | null;
  name: string;
  brand: string | null;
  categorySlug: string;
  categoryName: string;
  quantity: number;
  unitPrice: number;
  currentPrice: number | null;
  lineTotal: number;
  optional: boolean;
  locked: boolean;
  explanation: string | null;
  ratingAvg: number;
  ratingCount: number;
  availableQuantity: number;
  available: boolean;
};

export type Bundle = {
  id: string;
  goalId: string;
  goalTitle: string;
  runId: string | null;
  sourceBundleId: string | null;
  tier: Tier;
  name: string;
  status: 'RECOMMENDED' | 'DRAFT' | 'SAVED';
  editable: boolean;
  updatedAt: string;
  budget: number;
  totalPrice: number;
  currentTotal: number;
  summary: string | null;
  items: BundleItem[];
  compatibility: CompatibilityReport;
  purchasable: boolean;
  createdAt: string;
};

export type RecommendationRun = {
  id: string;
  goalId: string;
  goalTitle: string;
  status: 'OK' | 'INFEASIBLE';
  preferredTier: Tier;
  budget: number;
  bundles: Bundle[];
  notes: string[];
  reasons: string[];
  suggestions: string[];
  minimumFeasibleCost: number | null;
  stats: { algorithmVersion: string; nodesExplored: number; durationMs: number; complete: boolean };
  createdAt: string;
};

export const recommendationKeys = {
  run: (id: string) => ['recommendations', 'run', id] as const,
  latest: (goalId: string) => ['recommendations', 'latest', goalId] as const,
};

export function useRecommendationRun(runId: string) {
  return useQuery({ queryKey: recommendationKeys.run(runId), queryFn: () => api.get<RecommendationRun>(`/recommendations/${runId}`) });
}

/** The latest run for a goal, or null if recommendations were never generated. */
export function useLatestRun(goalId: string) {
  return useQuery({
    queryKey: recommendationKeys.latest(goalId),
    queryFn: async () => {
      try {
        return await api.get<RecommendationRun>(`/goals/${goalId}/recommendations/latest`);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

export function useGenerateRecommendations(goalId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<RecommendationRun>(`/goals/${goalId}/recommendations`),
    onSuccess: (run) => {
      queryClient.setQueryData(recommendationKeys.run(run.id), run);
      queryClient.setQueryData(recommendationKeys.latest(goalId), run);
    },
  });
}

/** Adds every product of a bundle to the cart; anonymous shoppers are sent to sign in first. */
export function useAddBundleToCart() {
  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const mutation = useMutation({
    mutationFn: (bundleId: string) => api.post<Cart>(`/cart/bundles/${bundleId}`),
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKey, cart);
      toast.show('Bundle added to your cart');
    },
    onError: (error) => toast.show(error instanceof ApiError ? error.message : 'Could not add the bundle', 'error'),
  });

  const addBundle = (bundleId: string) => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`);
      return;
    }
    mutation.mutate(bundleId);
  };
  return { addBundle, isPending: mutation.isPending, pendingId: mutation.variables };
}

export const tierLabels: Record<Tier, string> = {
  BUDGET: 'Budget',
  BALANCED: 'Balanced',
  PREMIUM: 'Premium',
  CUSTOM: 'Custom',
};
