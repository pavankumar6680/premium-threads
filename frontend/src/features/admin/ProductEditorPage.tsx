import { ArrowLeft, ExternalLink } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import { ApiError } from '../../lib/api';
import { formatDateTime } from '../../lib/format';
import { useAdminCategories, useAdminProduct, useSaveProduct, type AdminCategory, type AdminProduct, type ProductInput } from './api';
import { Card, Checkbox, errorMessage, fieldErrorsOf, PageHeader, SelectField, splitList, TextAreaField } from './components/AdminUi';

/** /admin/products/new and /admin/products/:id */
export function ProductEditorPage() {
  const { id } = useParams();
  const productId = id && id !== 'new' ? Number(id) : null;
  const product = useAdminProduct(productId);
  const categories = useAdminCategories();

  if ((productId !== null && product.isPending) || categories.isPending) return <PageSpinner />;
  if (product.isError || categories.isError) return <Alert tone="error">This product could not be loaded.</Alert>;
  // remount the form when a newer version is loaded (after saving or reloading)
  return <ProductForm key={`${productId}-${product.data?.version ?? 'new'}`} product={product.data ?? null} categories={categories.data} onReload={() => void product.refetch()} />;
}

type FormState = {
  categorySlug: string;
  sku: string;
  name: string;
  brand: string;
  description: string;
  price: string;
  qualityTier: string;
  active: boolean;
  tags: string;
  stock: string;
  attributes: Record<string, string>;
};

function initialState(product: AdminProduct | null): FormState {
  return {
    categorySlug: product?.categorySlug ?? '',
    sku: product?.sku ?? '',
    name: product?.name ?? '',
    brand: product?.brand ?? '',
    description: product?.description ?? '',
    price: product ? String(product.price) : '',
    qualityTier: String(product?.qualityTier ?? 2),
    active: product?.active ?? true,
    tags: product?.tags.join(', ') ?? '',
    stock: '0',
    attributes: Object.fromEntries(Object.entries(product?.attributes ?? {}).map(([k, v]) => [k, v.join(', ')])),
  };
}

function ProductForm({ product, categories, onReload }: { product: AdminProduct | null; categories: AdminCategory[]; onReload: () => void }) {
  const navigate = useNavigate();
  const toast = useToast();
  const save = useSaveProduct();
  const [form, setForm] = useState<FormState>(() => initialState(product));
  const update = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));

  const leaves = categories.filter((c) => c.leaf);
  const category = leaves.find((c) => c.slug === form.categorySlug);
  const errors = fieldErrorsOf(save.error);
  const stale = save.error instanceof ApiError && save.error.code === 'STALE_PRODUCT';
  const attributeError =
    save.error instanceof ApiError && typeof save.error.details.attribute === 'string' ? (save.error.details.attribute as string) : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const attributes: Record<string, string[]> = {};
    for (const definition of category?.attributes ?? []) {
      const raw = form.attributes[definition.key] ?? '';
      const values = definition.type === 'LIST' ? splitList(raw) : raw.trim() ? [raw.trim()] : [];
      if (values.length) attributes[definition.key] = values;
    }
    const input: ProductInput = {
      categorySlug: form.categorySlug,
      sku: form.sku.trim(),
      name: form.name.trim(),
      brand: form.brand.trim(),
      description: form.description,
      price: Number(form.price),
      qualityTier: Number(form.qualityTier),
      active: form.active,
      tags: splitList(form.tags.toLowerCase()),
      attributes,
      ...(product ? { version: product.version } : { stock: Number(form.stock) || 0 }),
    };
    save.mutate(
      { id: product?.id ?? null, input },
      {
        onSuccess: (saved) => {
          toast.show(product ? 'Product saved.' : 'Product created.');
          if (!product) navigate(`/admin/products/${saved.id}`, { replace: true });
        },
      },
    );
  };

  return (
    <>
      <Link to="/admin/products" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-slate-600 hover:text-slate-900">
        <ArrowLeft className="size-4" aria-hidden="true" /> Products
      </Link>
      <PageHeader
        title={product ? product.name : 'New product'}
        description={product ? `Last updated ${formatDateTime(product.updatedAt)} · version ${product.version}` : 'Products appear in the store as soon as they are saved on sale.'}
        action={
          product?.active && (
            <Link to={`/products/${product.slug}`} className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
              View in store <ExternalLink className="size-4" aria-hidden="true" />
            </Link>
          )
        }
      />
      <form onSubmit={submit} noValidate className="space-y-6">
        {stale ? (
          <Alert tone="error">
            {save.error?.message}{' '}
            <button type="button" onClick={onReload} className="font-semibold underline">Reload the latest version</button>
          </Alert>
        ) : save.error ? (
          <Alert tone="error">{errorMessage(save.error)}</Alert>
        ) : null}

        <Card title="Details">
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Name" value={form.name} onChange={(e) => update('name', e.target.value)} error={errors.name} required />
            <TextField label="Brand" value={form.brand} onChange={(e) => update('brand', e.target.value)} error={errors.brand} required />
            <TextField label="SKU" value={form.sku} onChange={(e) => update('sku', e.target.value)} error={errors.sku} hint="Letters, digits and dashes; stored in upper case." required />
            <SelectField label="Category" value={form.categorySlug} onChange={(e) => update('categorySlug', e.target.value)} error={errors.categorySlug} required>
              <option value="">Choose a category</option>
              {leaves.map((c) => (
                <option key={c.slug} value={c.slug}>{c.departmentName} · {c.name}</option>
              ))}
            </SelectField>
            <TextAreaField label="Description" className="sm:col-span-2" value={form.description} onChange={(e) => update('description', e.target.value)} error={errors.description} rows={4} />
          </div>
        </Card>

        <Card title="Price and availability">
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="Price (USD)" type="number" min={0} step="0.01" value={form.price} onChange={(e) => update('price', e.target.value)} error={errors.price} required />
            <SelectField label="Quality tier" value={form.qualityTier} onChange={(e) => update('qualityTier', e.target.value)} hint="Used by the recommendation engine.">
              <option value="1">1 · Budget</option>
              <option value="2">2 · Mid-range</option>
              <option value="3">3 · Premium</option>
            </SelectField>
            {product ? (
              <div>
                <p className="mb-1.5 text-sm font-medium text-slate-700">Stock</p>
                <p className="flex h-11 items-center text-sm text-slate-700">{product.stock} units · change it in the product list</p>
              </div>
            ) : (
              <TextField label="Initial stock" type="number" min={0} value={form.stock} onChange={(e) => update('stock', e.target.value)} error={errors.stock} />
            )}
          </div>
          <Checkbox className="mt-4" label="On sale" checked={form.active} onChange={(e) => update('active', e.target.checked)} />
        </Card>

        <Card title="Specifications">
          {!category ? (
            <p className="text-sm text-slate-500">Choose a category to see its specifications.</p>
          ) : category.attributes.length === 0 ? (
            <p className="text-sm text-slate-500">
              This category has no specifications yet. <Link to="/admin/categories" className="font-medium text-brand-700 hover:underline">Add some</Link>.
            </p>
          ) : (
            <>
              <p className="mb-4 text-sm text-slate-600">Filters and compatibility rules read these values, so keep them accurate.</p>
              <div className="grid gap-4 sm:grid-cols-2">
                {category.attributes.map((a) => {
                  const label = `${a.label}${a.unit ? ` (${a.unit})` : ''}`;
                  const error = attributeError === a.key ? save.error?.message : undefined;
                  const value = form.attributes[a.key] ?? '';
                  const set = (v: string) => update('attributes', { ...form.attributes, [a.key]: v });
                  return a.type === 'BOOL' ? (
                    <SelectField key={a.key} label={label} value={value} onChange={(e) => set(e.target.value)} error={error}>
                      <option value="">Not set</option>
                      <option value="true">Yes</option>
                      <option value="false">No</option>
                    </SelectField>
                  ) : (
                    <TextField
                      key={a.key}
                      label={label}
                      type={a.type === 'NUMBER' ? 'number' : 'text'}
                      step="any"
                      value={value}
                      onChange={(e) => set(e.target.value)}
                      error={error}
                      hint={a.type === 'LIST' ? 'Separate values with commas, e.g. HDMI, USB-C' : undefined}
                    />
                  );
                })}
              </div>
            </>
          )}
          <TextField
            className="mt-4"
            label="Tags"
            value={form.tags}
            onChange={(e) => update('tags', e.target.value)}
            error={Object.entries(errors).find(([k]) => k.startsWith('tags'))?.[1]}
            hint="Usage tags used for relevance, e.g. gaming, compact, ergonomic"
          />
        </Card>

        <div className="flex justify-end gap-3">
          <Link to="/admin/products" className="inline-flex h-10 items-center px-4 text-sm font-semibold text-slate-700 hover:underline">Cancel</Link>
          <Button type="submit" isLoading={save.isPending}>{product ? 'Save changes' : 'Create product'}</Button>
        </div>
      </form>
    </>
  );
}
