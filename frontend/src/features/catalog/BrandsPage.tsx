import clsx from 'clsx';
import { ArrowRight } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { buttonClasses } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { PageSpinner } from '../../components/ui/Spinner';
import { ApiError } from '../../lib/api';
import { pluralize } from '../../lib/format';
import { useBrand, useBrands, useProducts } from './api';
import { ProductCard, ProductGridSkeleton } from './components/ProductCard';

const DEPARTMENT_LABELS: Record<string, string> = { men: 'Men', women: 'Women', accessories: 'Accessories' };

/** /brands: every brand, filterable by department. */
export function BrandsPage() {
  const { data, isPending, isError } = useBrands();
  const [department, setDepartment] = useState('');
  const brands = (data ?? []).filter((b) => !department || b.categories.includes(department));

  return (
    <div className="mx-auto max-w-7xl px-4 py-10">
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-brand-600">The labels</p>
      <h1 className="mt-1 font-display text-4xl font-semibold tracking-tight sm:text-5xl">Brands</h1>
      <p className="mt-2 max-w-2xl text-slate-600">Fifteen labels under one roof, from sportswear to tailoring. Mix them freely: the outfit builder does.</p>
      <div role="tablist" aria-label="Department" className="mt-6 flex flex-wrap gap-2">
        {['', 'men', 'women', 'accessories'].map((d) => (
          <button
            key={d || 'all'}
            type="button"
            role="tab"
            aria-selected={department === d}
            onClick={() => setDepartment(d)}
            className={clsx('h-10 rounded-full px-4 text-sm font-semibold transition-colors', department === d ? 'bg-slate-900 text-white' : 'bg-white ring-1 ring-slate-300 hover:ring-slate-500')}
          >
            {d ? DEPARTMENT_LABELS[d] : 'All brands'}
          </button>
        ))}
      </div>
      {isPending ? (
        <PageSpinner />
      ) : isError ? (
        <Alert tone="error">Brands could not be loaded.</Alert>
      ) : (
        <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {brands.map((b, i) => (
            <li key={b.slug}>
              <Link
                to={`/brands/${b.slug}`}
                className={clsx(
                  'group flex h-full flex-col rounded-3xl p-6 shadow-card ring-1 ring-slate-200/70 transition hover:-translate-y-1 hover:shadow-lift',
                  i % 4 === 1 ? 'bg-violet-100' : i % 4 === 3 ? 'bg-brand-50' : 'bg-white',
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-display text-2xl font-semibold uppercase tracking-[0.12em]">{b.name}</h2>
                  {b.featured && <span className="rounded-full bg-slate-900 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide text-white">Featured</span>}
                </div>
                <p className="mt-3 flex-1 text-sm text-slate-600">{b.description}</p>
                <div className="mt-5 flex items-center justify-between text-sm">
                  <span className="text-slate-500">{pluralize(b.productCount, 'piece')} · {b.categories.map((c) => DEPARTMENT_LABELS[c] ?? c).join(', ')}</span>
                  <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" aria-hidden="true" />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <p className="mt-8 text-xs text-slate-500">Brand names are used as text only, for demonstration. Products, prices and stock are fictional and not affiliated with or endorsed by these brands.</p>
    </div>
  );
}

/** /brands/:slug: the brand's story and its products, filterable by department. */
export function BrandPage() {
  const { slug = '' } = useParams();
  const brand = useBrand(slug);
  const [params, setParams] = useSearchParams();
  const department = params.get('department') ?? '';
  const page = Number(params.get('page') ?? 0);
  const query = useMemo(() => {
    const q = new URLSearchParams({ brand: slug, size: '24', page: String(page) });
    if (department) q.set('category', department);
    if (params.get('sort')) q.set('sort', params.get('sort')!);
    return q.toString();
  }, [slug, department, page, params]);
  const products = useProducts(query);

  if (brand.isPending) return <PageSpinner />;
  if (brand.isError) {
    const notFound = brand.error instanceof ApiError && brand.error.status === 404;
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="font-display text-3xl font-semibold">{notFound ? 'Brand not found' : 'Something went wrong'}</h1>
        <Link to="/brands" className={buttonClasses('primary', 'md', 'mt-6')}>All brands</Link>
      </div>
    );
  }
  const b = brand.data;
  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete('page');
    setParams(next);
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
        <Link to="/" className="hover:text-slate-900">Home</Link>
        <span className="mx-1.5">/</span>
        <Link to="/brands" className="hover:text-slate-900">Brands</Link>
      </nav>
      <section className="relative mt-4 overflow-hidden rounded-3xl bg-slate-900 px-6 py-14 text-white sm:px-12">
        <div className="pointer-events-none absolute -right-20 -top-24 size-80 rounded-full bg-brand-500/30 blur-3xl" aria-hidden="true" />
        <div className="pointer-events-none absolute -bottom-24 left-1/3 size-72 rounded-full bg-violet-600/40 blur-3xl" aria-hidden="true" />
        <p className="relative text-xs font-bold uppercase tracking-[0.2em] text-brand-300">Brand</p>
        <h1 className="relative mt-2 font-display text-5xl font-semibold uppercase tracking-[0.12em] sm:text-6xl">{b.name}</h1>
        <p className="relative mt-3 max-w-xl text-white/80">{b.description}</p>
        <p className="relative mt-4 text-sm text-white/60">{pluralize(b.productCount, 'piece')} in {b.categories.map((c) => DEPARTMENT_LABELS[c] ?? c).join(', ')}</p>
      </section>

      <div className="mt-8 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Department">
          {['', ...b.categories].map((d) => (
            <button key={d || 'all'} type="button" role="tab" aria-selected={department === d} onClick={() => set('department', d)}
              className={clsx('h-9 rounded-full px-4 text-sm font-semibold', department === d ? 'bg-slate-900 text-white' : 'bg-white ring-1 ring-slate-300 hover:ring-slate-500')}>
              {d ? DEPARTMENT_LABELS[d] ?? d : 'Everything'}
            </button>
          ))}
        </div>
        <select aria-label="Sort products" value={params.get('sort') ?? ''} onChange={(e) => set('sort', e.target.value)}
          className="h-10 rounded-full border border-slate-300 bg-white px-3 text-sm font-medium">
          <option value="">Most popular</option>
          <option value="newest">New arrivals</option>
          <option value="price_asc">Price: low to high</option>
          <option value="price_desc">Price: high to low</option>
        </select>
      </div>

      <div className="mt-6">
        {products.isPending ? <ProductGridSkeleton /> : products.isError ? <Alert tone="error">Products could not be loaded.</Alert> : (
          <>
            <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 xl:grid-cols-4">
              {products.data.content.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
            <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(p) => set('page', p ? String(p) : '')} />
          </>
        )}
      </div>
    </div>
  );
}
