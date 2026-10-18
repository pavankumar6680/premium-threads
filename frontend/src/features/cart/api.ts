import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router';
import { useToast } from '../../components/ui/Toast';
import { api, ApiError } from '../../lib/api';
import { useCurrentUser } from '../auth/hooks';

export type CartItem = {
  productId: number;
  variantId: number;
  sku: string;
  slug: string;
  name: string;
  brand: string;
  categorySlug: string;
  size: string;
  color: string;
  colorHex: string | null;
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

/** Cart lines are identified by variant (size and color). */
export function useUpdateCartItem() {
  return useCartMutation(({ variantId, quantity }: { variantId: number; quantity: number }) =>
    api.put<Cart>(`/cart/items/${variantId}`, { quantity }),
  );
}

export function useRemoveCartItem() {
  return useCartMutation((variantId: number) => api.delete<Cart>(`/cart/items/${variantId}`));
}

/** "One size" and "Standard" are placeholders of single-option products, not worth showing. */
export function variantLabel(size: string | null | undefined, color: string | null | undefined): string {
  const parts = [color && color !== 'Standard' ? color : null, size && size !== 'One size' ? size : null].filter(Boolean);
  return parts.join(' · ');
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
  const mutation = useCartMutation(
    ({ productId, variantId, quantity }: { productId: number; variantId?: number; quantity: number }) =>
      api.post<Cart>('/cart/items', { productId, variantId, quantity }),
  );

  /**
   * Adds a variant, or a product sold in a single option. For a product with sizes and no variant chosen,
   * the shopper is taken to the product page ({@code slug}) to pick one.
   */
  const addToCart = (productId: number, quantity = 1, name?: string, variantId?: number, slug?: string) => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`);
      return;
    }
    mutation.mutate(
      { productId, variantId, quantity },
      {
        onSuccess: () => toast.show(name ? `Added ${name} to your cart` : 'Added to your cart'),
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'VARIANT_REQUIRED' && slug) {
            toast.show('Choose a size and color first');
            navigate(`/products/${slug}`);
            return;
          }
          toast.show(error instanceof ApiError ? error.message : 'Could not add to cart', 'error');
        },
      },
    );
  };

  return { addToCart, isPending: mutation.isPending, pendingProductId: mutation.variables?.productId };
}
