import type { Cart } from '../features/cart/api';
import type { Page, ProductSummary } from '../features/catalog/types';
import type { User } from '../features/auth/types';

export const ada: User = { id: 1, email: 'ada@example.com', fullName: 'Ada Lovelace', roles: ['CUSTOMER'] };

export const emptyPage: Page<never> = { content: [], page: 0, size: 24, totalElements: 0, totalPages: 0 };

export const emptyCart: Cart = {
  items: [],
  itemCount: 0,
  subtotal: 0,
  shipping: 0,
  tax: 0,
  total: 0,
  freeShippingThreshold: 100,
  checkoutReady: false,
};

export function product(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: 10,
    sku: 'MON-002',
    slug: 'vantix-27-qhd-monitor',
    name: 'Vantix 27 QHD Monitor',
    brand: 'Vantix',
    price: 249,
    ratingAvg: 4.5,
    ratingCount: 2900,
    qualityTier: 2,
    categorySlug: 'monitors',
    categoryName: 'Monitors',
    active: true,
    availableQuantity: 45,
    ...overrides,
  };
}

export function pageOf<T>(content: T[]): Page<T> {
  return { content, page: 0, size: 24, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}
