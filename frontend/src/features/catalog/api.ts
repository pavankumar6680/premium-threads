import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Brand, CategoryNode, Facets, Page, ProductCard, ProductDetail, Vocabulary } from './types';

export const catalogKeys = {
  categories: ['catalog', 'categories'] as const,
  products: (query: string) => ['catalog', 'products', query] as const,
  facets: (query: string) => ['catalog', 'facets', query] as const,
  product: (slug: string) => ['catalog', 'product', slug] as const,
  brands: ['catalog', 'brands'] as const,
  homeSections: ['catalog', 'home-sections'] as const,
  brand: (slug: string) => ['catalog', 'brand', slug] as const,
  vocabulary: ['catalog', 'vocabulary'] as const,
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
    queryFn: () => api.get<Page<ProductCard>>(`/products?${query}`),
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

/** A product rail on the homepage, managed by admins; query is a catalog filter such as "flag=trending". */
export type HomeSection = { id: number; slug: string; eyebrow: string; title: string; query: string; position: number };

export function useHomeSections() {
  return useQuery({ queryKey: catalogKeys.homeSections, queryFn: () => api.get<HomeSection[]>('/home/sections'), staleTime: 5 * 60_000 });
}

export function useBrands() {
  return useQuery({ queryKey: catalogKeys.brands, queryFn: () => api.get<Brand[]>('/brands'), staleTime: 10 * 60_000 });
}

export function useBrand(slug: string) {
  return useQuery({ queryKey: catalogKeys.brand(slug), queryFn: () => api.get<Brand>(`/brands/${encodeURIComponent(slug)}`) });
}

/** Styles and occasions (labels for filters and the outfit builder). */
export function useVocabulary() {
  return useQuery({ queryKey: catalogKeys.vocabulary, queryFn: () => api.get<Vocabulary>('/vocabulary'), staleTime: 30 * 60_000 });
}
