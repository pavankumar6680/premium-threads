import type { Cart } from '../features/cart/api';
import type { Page, ProductCard, ProductSummary } from '../features/catalog/types';
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
    sku: 'ZAR-002',
    slug: 'zara-linen-blend-shirt',
    name: 'Zara Linen Blend Shirt',
    brand: 'Zara',
    price: 249,
    ratingAvg: 4.5,
    ratingCount: 2900,
    qualityTier: 2,
    categorySlug: 'women-jackets',
    categoryName: 'Jackets',
    active: true,
    availableQuantity: 45,
    ...overrides,
  };
}

/** A listing card: the summary plus merchandising data (sold in one option unless overridden). */
export function card(overrides: Partial<ProductCard> = {}): ProductCard {
  return {
    ...product(),
    brandSlug: 'zara',
    compareAtPrice: null,
    gender: 'UNISEX',
    newArrival: false,
    trending: false,
    featured: false,
    colors: [],
    sizes: ['One size'],
    inStockSizes: ['One size'],
    imageUrl: null,
    ...overrides,
  };
}

export function pageOf<T>(content: T[]): Page<T> {
  return { content, page: 0, size: 24, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}
