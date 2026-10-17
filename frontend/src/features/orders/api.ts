import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { cartKey } from '../cart/api';
import type { Page } from '../catalog/types';

export type OrderStatus = 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export type OrderItem = {
  productId: number;
  slug: string | null;
  sku: string;
  name: string;
  brand: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
};

export type Order = {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  createdAt: string;
  cancelledAt: string | null;
  items: OrderItem[];
  subtotal: number;
  shipping: number;
  tax: number;
  total: number;
  currency: string;
  shippingAddress: {
    fullName: string;
    line1: string;
    line2: string | null;
    city: string;
    state: string;
    postalCode: string;
    country: string;
    phone: string | null;
  };
  payment: { reference: string; method: string; status: string; amount: number } | null;
  cancellable: boolean;
};

export type OrderSummary = {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  createdAt: string;
  total: number;
  currency: string;
  itemCount: number;
  itemNames: string[];
};

export const orderKeys = {
  all: ['orders'] as const,
  list: (page: number) => ['orders', 'list', page] as const,
  detail: (id: number) => ['orders', 'detail', id] as const,
};

export function useOrders(page: number) {
  return useQuery({
    queryKey: orderKeys.list(page),
    queryFn: () => api.get<Page<OrderSummary>>(`/orders?page=${page}`),
    placeholderData: (previous) => previous,
  });
}

export function useOrder(id: number) {
  return useQuery({ queryKey: orderKeys.detail(id), queryFn: () => api.get<Order>(`/orders/${id}`) });
}

/**
 * Places the order. The caller supplies one idempotency key per checkout attempt; resubmitting
 * (double click, network retry) with the same key returns the same order instead of creating another.
 */
export function useCheckout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ addressId, idempotencyKey }: { addressId: number; idempotencyKey: string }) =>
      api.post<Order>('/checkout', { addressId }, { headers: { 'Idempotency-Key': idempotencyKey } }),
    onSuccess: (order) => {
      queryClient.setQueryData(orderKeys.detail(order.id), order);
      void queryClient.invalidateQueries({ queryKey: cartKey });
      void queryClient.invalidateQueries({ queryKey: orderKeys.all, exact: false });
    },
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.post<Order>(`/orders/${id}/cancel`),
    onSuccess: (order) => {
      queryClient.setQueryData(orderKeys.detail(order.id), order);
      void queryClient.invalidateQueries({ queryKey: ['orders', 'list'] });
    },
  });
}

export const statusLabels: Record<OrderStatus, { label: string; className: string }> = {
  PAID: { label: 'Paid', className: 'bg-brand-100 text-brand-800' },
  SHIPPED: { label: 'Shipped', className: 'bg-amber-100 text-amber-800' },
  DELIVERED: { label: 'Delivered', className: 'bg-emerald-100 text-emerald-800' },
  CANCELLED: { label: 'Cancelled', className: 'bg-slate-200 text-slate-700' },
};
