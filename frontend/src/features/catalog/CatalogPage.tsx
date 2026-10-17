import { SlidersHorizontal, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { formatMoney, pluralize } from '../../lib/format';
import { useCategories, useFacets, useProducts } from './api';
import { formatAttributeValue } from './attributes';
import { FilterPanel } from './components/FilterPanel';
import { ProductCard, ProductGridSkeleton } from './components/ProductCard';

const PAGE_SIZE = '24';
const SORT_OPTIONS = [
  { value: '', label: 'Most popular' },
  { value: 'rating', label: 'Top rated' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'newest', label: 'Newest' },
];
const SCOPE_KEYS = ['q', 'category'];

export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const [filtersOpen, setFiltersOpen] = useState(false);

  const apiQuery = useMemo(() => {
    const p = new URLSearchParams(params);
    p.set('size', PAGE_SIZE);
    return p.toString();
  }, [params]);
  const facetQuery = useMemo(() => {
    const p = new URLSearchParams();
    SCOPE_KEYS.forEach((k) => params.get(k) && p.set(k, params.get(k)!));
    return p.toString();
  }, [params]);

  const products = useProducts(apiQuery);
  const facets = useFacets(facetQuery);
  const categories = useCategories();

  const q = params.get('q');
  const categorySlug = params.get('category');
  const department = categories.data?.find(
    (d) => d.slug === categorySlug || d.children.some((c) => c.slug === categorySlug),
  );
  const category = department?.slug === categorySlug ? department : department?.children.find((c) => c.slug === categorySlug);
  const title = q ? `Results for “${q}”` : (category?.name ?? 'All products');

  const update = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params);
    mutate(next);
    next.delete('page'); // any filter change starts from the first page
    setParams(next);
  };
  const toggle = (key: string, value: string) =>
    update((p) => {
      const values = p.getAll(key);
      p.delete(key);
      (values.includes(value) ? values.filter((v) => v !== value) : [...values, value]).forEach((v) => p.append(key, v));
    });
  const set = (updates: Record<string, string | null>) =>
    update((p) => Object.entries(updates).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k))));
  const clearRefinements = () =>
    setParams(new URLSearchParams([...params].filter(([k]) => SCOPE_KEYS.includes(k) || k === 'sort')));
  const goToPage = (page: number) => {
    const next = new URLSearchParams(params);
    if (page > 0) next.set('page', String(page));
    else next.delete('page');
    setParams(next);
    window.scrollTo({ top: 0 });
  };

  const chips = activeFilterChips(params, facets.data);
  const page = products.data;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
        <Link to="/" className="hover:text-slate-800">Home</Link>
        <span className="mx-1.5">/</span>
        <Link to="/shop" className="hover:text-slate-800">Shop</Link>
        {department && (
          <>
            <span className="mx-1.5">/</span>
            <Link to={`/shop?category=${department.slug}`} className="hover:text-slate-800">{department.name}</Link>
          </>
        )}
        {category && category !== department && (
          <>
            <span className="mx-1.5">/</span>
            <span className="text-slate-800">{category.name}</span>
          </>
        )}
      </nav>

      <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
          {page && <p className="mt-1 text-sm text-slate-600">{pluralize(page.totalElements, 'product')}</p>}
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFiltersOpen((o) => !o)} aria-expanded={filtersOpen}>
            <SlidersHorizontal className="size-4" aria-hidden="true" /> Filters
          </Button>
          <label className="flex items-center gap-2 text-sm">
            <span className="hidden text-slate-600 sm:inline">Sort by</span>
            <select
              className="h-9 rounded-lg border border-slate-300 bg-white px-2 text-sm focus:border-brand-500 focus:outline-none"
              value={params.get('sort') ?? ''}
              onChange={(e) => set({ sort: e.target.value || null })}
              aria-label="Sort products"
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div className="mt-6 lg:grid lg:grid-cols-[250px_1fr] lg:gap-8">
        <aside className={filtersOpen ? 'mb-6 rounded-2xl border border-slate-200 bg-white p-4 lg:mb-0 lg:border-0 lg:bg-transparent lg:p-0' : 'hidden lg:block'} aria-label="Filters">
          <FilterPanel params={params} categories={categories.data ?? []} facets={facets.data} onToggle={toggle} onSet={set} />
        </aside>

        <section aria-label="Products" aria-busy={products.isFetching}>
          {chips.length > 0 && (
            <div className="mb-4 flex flex-wrap items-center gap-2">
              {chips.map((chip) => (
                <button
                  key={`${chip.key}=${chip.value}`}
                  type="button"
                  onClick={() => (chip.multi ? toggle(chip.key, chip.value) : set({ [chip.key]: null }))}
                  className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-800 ring-1 ring-brand-200 hover:bg-brand-100"
                >
                  {chip.label}
                  <X className="size-3" aria-label="Remove filter" />
                </button>
              ))}
              <button type="button" onClick={clearRefinements} className="text-xs font-semibold text-slate-600 hover:underline">
                Clear all
              </button>
            </div>
          )}

          {products.isError ? (
            <Alert tone="error">{products.error.message}</Alert>
          ) : !page ? (
            <ProductGridSkeleton />
          ) : page.content.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
              <h2 className="text-lg font-semibold">No products match</h2>
              <p className="mt-1 text-sm text-slate-600">Try removing a filter or searching for something else.</p>
              {chips.length > 0 && (
                <Button variant="secondary" className="mt-4" onClick={clearRefinements}>Clear filters</Button>
              )}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                {page.content.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
              <Pagination page={page.page} totalPages={page.totalPages} onChange={goToPage} />
            </>
          )}
        </section>
      </div>
    </div>
  );
}

type Chip = { key: string; value: string; label: string; multi: boolean };

function activeFilterChips(params: URLSearchParams, facets: ReturnType<typeof useFacets>['data']): Chip[] {
  const chips: Chip[] = params.getAll('brand').map((b) => ({ key: 'brand', value: b, label: b, multi: true }));
  const min = params.get('minPrice');
  const max = params.get('maxPrice');
  if (min) chips.push({ key: 'minPrice', value: min, label: `From ${formatMoney(Number(min))}`, multi: false });
  if (max) chips.push({ key: 'maxPrice', value: max, label: `Up to ${formatMoney(Number(max))}`, multi: false });
  const rating = params.get('minRating');
  if (rating) chips.push({ key: 'minRating', value: rating, label: `${rating}+ stars`, multi: false });
  for (const [key, value] of params) {
    if (!key.startsWith('attr.')) continue;
    const facet = facets?.attributes.find((a) => `attr.${a.key}` === key);
    const label = facet ? `${facet.label}: ${formatAttributeValue(facet.type, facet.unit, value)}` : value;
    chips.push({ key, value, label, multi: true });
  }
  return chips;
}
