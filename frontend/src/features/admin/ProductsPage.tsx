import clsx from 'clsx';
import { Check, Plus, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button, buttonClasses } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { errorMessage } from './components/AdminUi';
import { formatMoney } from '../../lib/format';
import {
  useAdminCategories,
  useAdminProducts,
  useSetProductActive,
  useSetStock,
  type AdminProductRow,
  type ProductFilters,
} from './api';
import { Badge, PageHeader, TableWrap, td, th, Toggle } from './components/AdminUi';

const LOW_STOCK = 5;

export function ProductsPage() {
  const [params, setParams] = useSearchParams();
  const filters: ProductFilters = {
    q: params.get('q') ?? '',
    category: params.get('category') ?? '',
    status: (params.get('status') as ProductFilters['status']) ?? '',
    lowStock: params.get('lowStock') === '1',
    sort: (params.get('sort') as ProductFilters['sort']) ?? 'name',
    page: Number(params.get('page') ?? 0),
  };
  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '') next.delete(key);
      else next.set(key, value);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  const { data, isPending, isError, isFetching } = useAdminProducts(filters);
  const { data: categories } = useAdminCategories();
  const [search, setSearch] = useState(filters.q);
  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    set({ q: search.trim() });
  };

  const select = 'h-10 rounded-lg border border-slate-300 bg-white px-2 text-sm focus:border-brand-500 focus:outline-none';
  return (
    <>
      <PageHeader
        title="Products"
        description="Edit details, prices and specifications, adjust stock, or take products off sale."
        action={
          <Link to="/admin/products/new" className={buttonClasses('primary')}>
            <Plus className="size-4" aria-hidden="true" /> New product
          </Link>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <form role="search" onSubmit={onSearch} className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <input
            type="search"
            aria-label="Search by name, SKU or brand"
            placeholder="Name, SKU or brand"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none"
          />
        </form>
        <select aria-label="Category" className={select} value={filters.category} onChange={(e) => set({ category: e.target.value })}>
          <option value="">All categories</option>
          {categories?.filter((c) => c.leaf).map((c) => (
            <option key={c.slug} value={c.slug}>{c.departmentName} · {c.name}</option>
          ))}
        </select>
        <select aria-label="Status" className={select} value={filters.status} onChange={(e) => set({ status: e.target.value })}>
          <option value="">Any status</option>
          <option value="active">On sale</option>
          <option value="inactive">Off sale</option>
        </select>
        <select aria-label="Sort" className={select} value={filters.sort} onChange={(e) => set({ sort: e.target.value === 'name' ? null : e.target.value })}>
          <option value="name">Name</option>
          <option value="price">Price</option>
          <option value="stock">Stock (lowest first)</option>
          <option value="updated">Recently updated</option>
        </select>
        <label className="inline-flex h-10 items-center gap-2 rounded-lg px-2 text-sm text-slate-700">
          <input type="checkbox" checked={filters.lowStock} onChange={(e) => set({ lowStock: e.target.checked ? '1' : null })} className="size-4 rounded border-slate-300" />
          Low stock only
        </label>
      </div>

      {isPending ? (
        <div className="flex justify-center py-16"><Spinner /></div>
      ) : isError ? (
        <Alert tone="error">Products could not be loaded.</Alert>
      ) : data.content.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-slate-300 p-10 text-center text-sm text-slate-500">No products match these filters.</p>
      ) : (
        <>
          <p className="mb-2 text-sm text-slate-500" aria-live="polite">
            {data.totalElements} products{isFetching && ' · updating…'}
          </p>
          <TableWrap>
            <thead className="bg-slate-50">
              <tr>
                <th className={th}>Product</th>
                <th className={th}>Category</th>
                <th className={clsx(th, 'text-right')}>Price</th>
                <th className={th}>Stock</th>
                <th className={th}>On sale</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((p) => <ProductRow key={p.id} product={p} />)}
            </tbody>
          </TableWrap>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={(page) => set({ page: String(page) })} />
        </>
      )}
    </>
  );
}

function ProductRow({ product }: { product: AdminProductRow }) {
  const toast = useToast();
  const setStock = useSetStock();
  const setActive = useSetProductActive();
  const [stock, setStockValue] = useState(String(product.stock));
  const dirty = stock !== String(product.stock);

  const saveStock = (e: FormEvent) => {
    e.preventDefault();
    const quantity = Number(stock);
    if (!Number.isInteger(quantity) || quantity < 0) {
      toast.show('Stock must be a whole number of 0 or more.', 'error');
      return;
    }
    setStock.mutate(
      { id: product.id, quantity },
      {
        onSuccess: () => toast.show(`Stock for ${product.name} set to ${quantity}.`),
        onError: (error) => toast.show(errorMessage(error) ?? 'Stock could not be saved.', 'error'),
      },
    );
  };

  return (
    <tr className={clsx(!product.active && 'bg-slate-50/70 text-slate-500')}>
      <td className={td}>
        <Link to={`/admin/products/${product.id}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">
          {product.name}
        </Link>
        <p className="text-xs text-slate-500">{product.sku} · {product.brand}</p>
      </td>
      <td className={clsx(td, 'whitespace-nowrap')}>{product.categoryName}</td>
      <td className={clsx(td, 'whitespace-nowrap text-right font-medium')}>{formatMoney(product.price)}</td>
      <td className={td}>
        {product.variantCount > 1 ? (
          <Link to={`/admin/products/${product.id}`} className="inline-flex items-center gap-2 whitespace-nowrap hover:underline">
            <span className={clsx('font-medium', product.stock <= LOW_STOCK && 'text-amber-700')}>{product.stock}</span>
            <span className="text-xs text-slate-500">in {product.variantCount} sizes/colors</span>
          </Link>
        ) : (
          <form onSubmit={saveStock} className="flex items-center gap-1.5">
            <input
              type="number"
              min={0}
              aria-label={`Stock for ${product.name}`}
              value={stock}
              onChange={(e) => setStockValue(e.target.value)}
              className={clsx(
                'h-9 w-20 rounded-lg border px-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-100',
                product.stock <= LOW_STOCK ? 'border-amber-400' : 'border-slate-300',
              )}
            />
            {dirty ? (
              <Button type="submit" size="sm" variant="secondary" isLoading={setStock.isPending} aria-label={`Save stock for ${product.name}`}>
                <Check className="size-4" aria-hidden="true" />
              </Button>
            ) : product.stock === 0 ? (
              <Badge tone="red">Sold out</Badge>
            ) : product.stock <= LOW_STOCK ? (
              <Badge tone="amber">Low</Badge>
            ) : null}
          </form>
        )}
      </td>
      <td className={td}>
        <Toggle
          checked={product.active}
          label={`${product.name} on sale`}
          disabled={setActive.isPending}
          onChange={(active) =>
            setActive.mutate(
              { id: product.id, active },
              {
                onSuccess: () => toast.show(active ? `${product.name} is on sale again.` : `${product.name} is off sale.`),
                onError: (error) => toast.show(errorMessage(error) ?? 'Could not change the status.', 'error'),
              },
            )
          }
        />
      </td>
    </tr>
  );
}
