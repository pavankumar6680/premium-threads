import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { CategoryNode, Facets, Page, ProductDetail, ProductSummary } from './types';

export const catalogKeys = {
  categories: ['catalog', 'categories'] as const,
  products: (query: string) => ['catalog', 'products', query] as const,
  facets: (query: string) => ['catalog', 'facets', query] as const,
  product: (slug: string) => ['catalog', 'product', slug] as const,
};

export function useCategories() {
  return useQuery({
    queryKey: catalogKeys.categories,
    queryFn: () => api.get<CategoryNode[]>('/categories'),
    staleTime: 10 * 60_000,
  });
}

/** `query` uses the API's own parameter names (q, category, brand, attr.*, sort, page…). */
export function useProducts(query: string) {
  return useQuery({
    queryKey: catalogKeys.products(query),
    queryFn: () => api.get<Page<ProductSummary>>(`/products?${query}`),
    placeholderData: (previous) => previous,
  });
}

export function useFacets(query: string) {
  return useQuery({
    queryKey: catalogKeys.facets(query),
    queryFn: () => api.get<Facets>(`/products/facets?${query}`),
    staleTime: 5 * 60_000,
  });
}

export function useProduct(slug: string) {
  return useQuery({
    queryKey: catalogKeys.product(slug),
    queryFn: () => api.get<ProductDetail>(`/products/${encodeURIComponent(slug)}`),
  });
}
