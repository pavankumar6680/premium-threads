export type ProductSummary = {
  id: number;
  sku: string;
  slug: string;
  name: string;
  brand: string;
  price: number;
  ratingAvg: number;
  ratingCount: number;
  qualityTier: number;
  categorySlug: string;
  categoryName: string;
  active: boolean;
  availableQuantity: number;
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CategoryNode = {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  productCount: number;
  children: CategoryNode[];
};

export type AttributeValue = {
  key: string;
  label: string;
  type: 'TEXT' | 'NUMBER' | 'BOOL' | 'LIST';
  unit: string | null;
  values: string[];
};

export type ProductDetail = {
  product: ProductSummary;
  description: string;
  department: { slug: string; name: string } | null;
  attributes: AttributeValue[];
  tags: string[];
  images: { url: string; altText: string | null }[];
  related: ProductSummary[];
};

export type FacetValue = { value: string; count: number };

export type Facets = {
  brands: FacetValue[];
  minPrice: number | null;
  maxPrice: number | null;
  attributes: { key: string; label: string; type: AttributeValue['type']; unit: string | null; values: FacetValue[] }[];
};
