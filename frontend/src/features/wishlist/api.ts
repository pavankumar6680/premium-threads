import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router';
import { useToast } from '../../components/ui/Toast';
import { api } from '../../lib/api';
import { useCurrentUser } from '../auth/hooks';
import type { ProductSummary } from '../catalog/types';

export const wishlistKey = ['wishlist'] as const;

export function useWishlist() {
  const { data: user } = useCurrentUser();
  return useQuery({
    queryKey: wishlistKey,
    queryFn: () => api.get<ProductSummary[]>('/wishlist'),
    enabled: !!user,
  });
}

/** Returns whether a product is saved and a toggle that sends anonymous visitors to sign in. */
export function useWishlistToggle(productId: number) {
  const { data: user } = useCurrentUser();
  const { data: wishlist } = useWishlist();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const saved = !!wishlist?.some((p) => p.id === productId);

  const mutation = useMutation({
    mutationFn: (save: boolean) =>
      save ? api.put<void>(`/wishlist/${productId}`) : api.delete<void>(`/wishlist/${productId}`),
    onSuccess: (_, save) => {
      void queryClient.invalidateQueries({ queryKey: wishlistKey });
      toast.show(save ? 'Saved to your wishlist' : 'Removed from your wishlist');
    },
    onError: () => toast.show('Could not update your wishlist', 'error'),
  });

  const toggle = () => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`);
      return;
    }
    mutation.mutate(!saved);
  };

  return { saved, toggle, isPending: mutation.isPending };
}
