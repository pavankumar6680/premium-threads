import { SlidersHorizontal, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { formatMoney, pluralize } from '../../lib/format';
import { useCategories, useFacets, useProducts, useVocabulary } from './api';
import { formatAttributeValue } from './attributes';
import { FilterPanel, FLAG_LABELS } from './components/FilterPanel';
import { ProductCard, ProductGridSkeleton } from './components/ProductCard';

const PAGE_SIZE = '24';
const SORT_OPTIONS = [
  { value: '', label: 'Most popular' },
  { value: 'newest', label: 'New arrivals' },
  { value: 'discount', label: 'Biggest discount' },
  { value: 'rating', label: 'Top rated' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
];

/** Editorial banners for the three departments (photos: see public/images/editorial/CREDITS.md). */
const DEPARTMENT_BANNERS: Record<string, { image: string; tagline: string }> = {
  men: { image: '/images/editorial/men.jpg', tagline: 'Sharp shirts, easy denim and layers that work every day.' },
  women: { image: '/images/editorial/women.jpg', tagline: 'Dresses, tailoring and weekend favourites from 15 brands.' },
  accessories: { image: '/images/editorial/accessories.jpg', tagline: 'Watches, bags, shades and the finishing touches.' },
};
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
  const vocabulary = useVocabulary();

  const q = params.get('q');
  const categorySlug = params.get('category');
  const department = categories.data?.find(
    (d) => d.slug === categorySlug || d.children.some((c) => c.slug === categorySlug),
  );
  const category = department?.slug === categorySlug ? department : department?.children.find((c) => c.slug === categorySlug);
  const styleName = vocabulary.data?.styles.find((s) => s.slug === params.get('style'))?.name;
  const occasionName = vocabulary.data?.occasions.find((o) => o.slug === params.get('occasion'))?.name;
  const brandsSelected = params.getAll('brand');
  const leafTitle = category && category !== department && department && department.slug !== 'accessories'
    ? `${department.name}'s ${category.name.toLowerCase()}`
    : category?.name;
  const title = q
    ? `Results for “${q}”`
    : (leafTitle ?? (params.get('flag') === 'sale' ? 'Sale'
      : styleName ? `The ${styleName.toLowerCase()} edit`
        : occasionName ? `${occasionName} edit`
          : brandsSelected.length === 1 ? brandsSelected[0] : 'All fashion'));
  const banner = !q && department && category === department ? DEPARTMENT_BANNERS[department.slug] : undefined;

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

  const chips = activeFilterChips(params, facets.data, vocabulary.data?.styles ?? []);
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

      {banner && department && (
        <div className="relative mt-4 overflow-hidden rounded-3xl bg-slate-900">
          <img src={banner.image} alt="" className="absolute inset-0 h-full w-full object-cover object-top opacity-70" />
          <div className="absolute inset-0 bg-gradient-to-r from-slate-900/85 via-slate-900/40 to-transparent" aria-hidden="true" />
          <div className="relative px-6 py-12 text-white sm:px-10 sm:py-16">
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-brand-300">Shop</p>
            <h2 className="mt-2 font-display text-4xl font-semibold sm:text-5xl">{department.name}</h2>
            <p className="mt-2 max-w-md text-white/85">{banner.tagline}</p>
            <div className="mt-5 flex flex-wrap gap-2">
              {department.children.map((c) => (
                <Link key={c.slug} to={`/shop?category=${c.slug}`} className="rounded-full bg-white/15 px-3.5 py-1.5 text-sm font-semibold backdrop-blur hover:bg-white hover:text-slate-900">
                  {c.name}
                </Link>
              ))}
            </div>
          </div>
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
          {page && <p className="mt-1 text-sm text-slate-600">{pluralize(page.totalElements, 'piece')}</p>}
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFiltersOpen((o) => !o)} aria-expanded={filtersOpen}>
            <SlidersHorizontal className="size-4" aria-hidden="true" /> Filters{chips.length > 0 ? ` (${chips.length})` : ''}
          </Button>
          <label className="flex items-center gap-2 text-sm">
            <span className="hidden text-slate-600 sm:inline">Sort by</span>
            <select
              className="h-10 rounded-full border border-slate-300 bg-white px-3 text-sm font-medium focus:border-violet-500 focus:outline-none"
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
          <FilterPanel params={params} categories={categories.data ?? []} facets={facets.data} styles={vocabulary.data?.styles} onToggle={toggle} onSet={set} />
        </aside>

        <section aria-label="Products" aria-busy={products.isFetching}>
          {chips.length > 0 && (
            <div className="mb-4 flex flex-wrap items-center gap-2">
              {chips.map((chip) => (
                <button
                  key={`${chip.key}=${chip.value}`}
                  type="button"
                  onClick={() => (chip.multi ? toggle(chip.key, chip.value) : set({ [chip.key]: null }))}
                  className="inline-flex items-center gap-1 rounded-full bg-violet-100 px-3 py-1.5 text-xs font-semibold text-violet-800 hover:bg-violet-200"
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
            <div className="rounded-3xl bg-white px-6 py-16 text-center shadow-card">
              <h2 className="font-display text-2xl font-semibold">Nothing matches… yet</h2>
              <p className="mt-2 text-sm text-slate-600">Try another size or color, remove a filter, or search for something else.</p>
              {chips.length > 0 && (
                <Button variant="secondary" className="mt-4" onClick={clearRefinements}>Clear filters</Button>
              )}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 xl:grid-cols-4">
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

function activeFilterChips(
  params: URLSearchParams,
  facets: ReturnType<typeof useFacets>['data'],
  styles: { slug: string; name: string }[],
): Chip[] {
  const chips: Chip[] = params.getAll('brand').map((b) => ({ key: 'brand', value: b, label: b, multi: true }));
  params.getAll('flag').forEach((f) => chips.push({ key: 'flag', value: f, label: FLAG_LABELS[f] ?? f, multi: true }));
  params.getAll('sizes').forEach((v) => chips.push({ key: 'sizes', value: v, label: `Size ${v}`, multi: true }));
  params.getAll('color').forEach((v) => chips.push({ key: 'color', value: v, label: v, multi: true }));
  params.getAll('style').forEach((v) => chips.push({ key: 'style', value: v, label: styles.find((x) => x.slug === v)?.name ?? v, multi: true }));
  params.getAll('occasion').forEach((v) => chips.push({ key: 'occasion', value: v, label: v, multi: true }));
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
