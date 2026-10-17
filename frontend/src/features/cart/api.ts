import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router';
import { useToast } from '../../components/ui/Toast';
import { api, ApiError } from '../../lib/api';
import { useCurrentUser } from '../auth/hooks';

export type CartItem = {
  productId: number;
  sku: string;
  slug: string;
  name: string;
  brand: string;
  categorySlug: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  availableQuantity: number;
  available: boolean;
};

export type Cart = {
  items: CartItem[];
  itemCount: number;
  subtotal: number;
  shipping: number;
  tax: number;
  total: number;
  freeShippingThreshold: number;
  checkoutReady: boolean;
};

export const cartKey = ['cart'] as const;

export function useCart() {
  const { data: user } = useCurrentUser();
  return useQuery({
    queryKey: cartKey,
    queryFn: () => api.get<Cart>('/cart'),
    enabled: !!user,
  });
}

function useCartMutation<TVars>(mutationFn: (vars: TVars) => Promise<Cart>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (cart) => queryClient.setQueryData(cartKey, cart),
  });
}

export function useUpdateCartItem() {
  return useCartMutation(({ productId, quantity }: { productId: number; quantity: number }) =>
    api.put<Cart>(`/cart/items/${productId}`, { quantity }),
  );
}

export function useRemoveCartItem() {
  return useCartMutation((productId: number) => api.delete<Cart>(`/cart/items/${productId}`));
}

/**
 * "Add to cart" as used by product cards and pages: sends anonymous visitors to sign in first and
 * reports the outcome with a toast.
 */
export function useAddToCart() {
  const { data: user } = useCurrentUser();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const mutation = useCartMutation(({ productId, quantity }: { productId: number; quantity: number }) =>
    api.post<Cart>('/cart/items', { productId, quantity }),
  );

  const addToCart = (productId: number, quantity = 1, name?: string) => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`);
      return;
    }
    mutation.mutate(
      { productId, quantity },
      {
        onSuccess: () => toast.show(name ? `Added ${name} to your cart` : 'Added to your cart'),
        onError: (error) => toast.show(error instanceof ApiError ? error.message : 'Could not add to cart', 'error'),
      },
    );
  };

  return { addToCart, isPending: mutation.isPending, pendingProductId: mutation.variables?.productId };
}
