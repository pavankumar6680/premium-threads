import clsx from 'clsx';
import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import { useAdminBrands, useSaveBrand, type AdminBrand } from './api';
import { Badge, Card, Checkbox, errorMessage, fieldErrorsOf, PageHeader, TableWrap, td, TextAreaField, th, Toggle } from './components/AdminUi';

/** Brands are data: add, rename, feature on the homepage or retire them without a deployment. */
export function BrandsAdminPage() {
  const brands = useAdminBrands();
  const save = useSaveBrand();
  const toast = useToast();
  const [editing, setEditing] = useState<AdminBrand | 'new' | null>(null);

  if (brands.isPending) return <PageSpinner />;
  if (brands.isError) return <Alert tone="error">Brands could not be loaded.</Alert>;

  const toggle = (b: AdminBrand, patch: Partial<Pick<AdminBrand, 'featured' | 'active'>>) =>
    save.mutate(
      { id: b.id, input: { name: b.name, description: b.description, featured: b.featured, active: b.active, ...patch } },
      { onError: (e) => toast.show(errorMessage(e) ?? 'Could not update the brand', 'error') },
    );

  return (
    <>
      <PageHeader
        title="Brands"
        description="Featured brands appear on the homepage. Retired brands stay on past orders but leave the store."
        action={!editing && <Button onClick={() => setEditing('new')}><Plus className="size-4" aria-hidden="true" /> New brand</Button>}
      />
      <Alert tone="info">Demo data: brand names are used for illustration only, without logos or product photos from the brands.</Alert>
      {editing && (
        <div className="mt-4">
          <BrandEditor key={editing === 'new' ? 'new' : editing.id} brand={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />
        </div>
      )}
      <div className="mt-4">
        <TableWrap>
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className={th}>Brand</th>
                <th className={th}>Products</th>
                <th className={th}>Featured</th>
                <th className={th}>Active</th>
                <th className={th}><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {brands.data.map((b) => (
                <tr key={b.id} className={clsx(!b.active && 'text-slate-400')}>
                  <td className={td}>
                    <p className="font-medium text-slate-900">{b.name}</p>
                    <p className="font-mono text-xs text-slate-500">{b.slug}</p>
                  </td>
                  <td className={td}>
                    <Link to={`/brands/${b.slug}`} className="hover:underline">{b.productCount}</Link>
                  </td>
                  <td className={td}><Toggle checked={b.featured} label={`Feature ${b.name}`} onChange={(featured) => toggle(b, { featured })} disabled={save.isPending} /></td>
                  <td className={td}><Toggle checked={b.active} label={`${b.name} active`} onChange={(active) => toggle(b, { active })} disabled={save.isPending} /></td>
                  <td className={td}>
                    <button type="button" className="text-sm font-semibold text-brand-700 hover:underline" onClick={() => setEditing(b)}>Edit</button>
                    {!b.active && <span className="ml-2"><Badge>Retired</Badge></span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      </div>
    </>
  );
}

function BrandEditor({ brand, onDone }: { brand: AdminBrand | null; onDone: () => void }) {
  const save = useSaveBrand();
  const toast = useToast();
  const [form, setForm] = useState({
    name: brand?.name ?? '',
    slug: brand?.slug ?? '',
    description: brand?.description ?? '',
    featured: brand?.featured ?? false,
    active: brand?.active ?? true,
  });
  const errors = fieldErrorsOf(save.error);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(
      {
        id: brand?.id ?? null,
        input: { name: form.name.trim(), ...(form.slug.trim() && { slug: form.slug.trim() }), description: form.description, featured: form.featured, active: form.active },
      },
      {
        onSuccess: () => {
          toast.show(brand ? 'Brand saved.' : 'Brand created.');
          onDone();
        },
      },
    );
  };

  return (
    <Card title={brand ? `Edit ${brand.name}` : 'New brand'}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {save.error && !Object.keys(errors).length && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} error={errors.name} required />
          <TextField label="Slug" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} error={errors.slug} hint={brand ? 'Changing it changes the brand page address.' : 'Leave empty to derive it from the name.'} />
          <TextAreaField label="Description" className="sm:col-span-2" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} error={errors.description} />
        </div>
        <div className="flex flex-wrap gap-6">
          <Checkbox label="Featured on the homepage" checked={form.featured} onChange={(e) => setForm({ ...form, featured: e.target.checked })} />
          <Checkbox label="Active" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
        </div>
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
          <Button type="submit" isLoading={save.isPending}>{brand ? 'Save brand' : 'Create brand'}</Button>
        </div>
      </form>
    </Card>
  );
}
