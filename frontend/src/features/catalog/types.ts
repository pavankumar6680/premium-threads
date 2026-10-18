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

export type Gender = 'MEN' | 'WOMEN' | 'UNISEX';

export type ColorOption = { name: string; hex: string | null };

/** A product as listed in the storefront: the summary plus merchandising data. */
export type ProductCard = ProductSummary & {
  brandSlug: string;
  compareAtPrice: number | null;
  gender: Gender;
  newArrival: boolean;
  trending: boolean;
  featured: boolean;
  colors: ColorOption[];
  sizes: string[];
  inStockSizes: string[];
  imageUrl: string | null;
};

/** One size × color option with its own stock. */
export type Variant = {
  id: number;
  sku: string;
  size: string;
  color: string;
  colorHex: string | null;
  availableQuantity: number;
};

export type NamedRef = { slug: string; name: string };

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
  product: ProductCard;
  description: string;
  department: { slug: string; name: string } | null;
  brandInfo: { slug: string; name: string; description: string };
  sizeScale: 'ALPHA' | 'WAIST' | 'ONE_SIZE' | null;
  material: string | null;
  care: string | null;
  variants: Variant[];
  styles: NamedRef[];
  occasions: NamedRef[];
  attributes: AttributeValue[];
  tags: string[];
  images: { url: string; altText: string | null; color: string | null }[];
  related: ProductCard[];
  completeTheLook: ProductCard[];
};

export type FacetValue = { value: string; count: number };

export type Facets = {
  brands: FacetValue[];
  minPrice: number | null;
  maxPrice: number | null;
  sizes: FacetValue[];
  colors: (FacetValue & { hex: string | null })[];
  styles: FacetValue[];
  attributes: { key: string; label: string; type: AttributeValue['type']; unit: string | null; values: FacetValue[] }[];
};
