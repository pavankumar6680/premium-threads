import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import { useAddAttribute, useAdminCategories, useCreateCategory, type AdminCategory, type AttributeType } from './api';
import { Badge, Card, Checkbox, errorMessage, fieldErrorsOf, PageHeader, SelectField } from './components/AdminUi';

export function CategoriesPage() {
  const { data, isPending, isError } = useAdminCategories();
  const [adding, setAdding] = useState(false);
  if (isPending) return <PageSpinner />;
  if (isError) return <Alert tone="error">Categories could not be loaded.</Alert>;

  const departments = data.filter((c) => !c.departmentSlug);
  return (
    <>
      <PageHeader
        title="Categories and specifications"
        description="A new category or specification is immediately available to products, filters, compatibility rules, goal templates and the goal interpreter."
        action={!adding && <Button onClick={() => setAdding(true)}><Plus className="size-4" aria-hidden="true" /> New category</Button>}
      />
      {adding && <NewCategory departments={departments} onDone={() => setAdding(false)} />}
      <div className="space-y-8">
        {departments.map((d) => (
          <section key={d.slug}>
            <h2 className="mb-3 text-lg font-semibold text-slate-900">{d.name}</h2>
            <div className="grid gap-4 lg:grid-cols-2">
              {data.filter((c) => c.departmentSlug === d.slug).map((c) => <CategoryCard key={c.slug} category={c} />)}
            </div>
          </section>
        ))}
      </div>
    </>
  );
}

function CategoryCard({ category }: { category: AdminCategory }) {
  const [adding, setAdding] = useState(false);
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-900">{category.name}</h3>
          <p className="font-mono text-xs text-slate-500">{category.slug} · {category.productCount} products</p>
        </div>
        {!adding && (
          <Button variant="ghost" size="sm" onClick={() => setAdding(true)} aria-label={`Add a specification to ${category.name}`}>
            <Plus className="size-4" aria-hidden="true" /> Spec
          </Button>
        )}
      </div>
      {category.attributes.length > 0 ? (
        <ul className="mt-3 flex flex-wrap gap-1.5">
          {category.attributes.map((a) => (
            <li key={a.key}>
              <Badge tone={a.filterable ? 'brand' : 'slate'}>
                {a.label}{a.unit ? ` (${a.unit})` : ''} · {a.type.toLowerCase()}
              </Badge>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-3 text-sm text-slate-500">No specifications yet.</p>
      )}
      {adding && <NewAttribute category={category} onDone={() => setAdding(false)} />}
    </div>
  );
}

function NewCategory({ departments, onDone }: { departments: AdminCategory[]; onDone: () => void }) {
  const create = useCreateCategory();
  const toast = useToast();
  const [form, setForm] = useState({ departmentSlug: departments[0]?.slug ?? '', slug: '', name: '', description: '' });
  const errors = fieldErrorsOf(create.error);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate(form, {
      onSuccess: (c) => {
        toast.show(`Category ${c.name} created.`);
        onDone();
      },
    });
  };
  return (
    <Card title="New category" className="mb-8">
      <form onSubmit={submit} noValidate className="space-y-4">
        {create.error && <Alert tone="error">{errorMessage(create.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-3">
          <SelectField label="Department" value={form.departmentSlug} onChange={(e) => setForm({ ...form, departmentSlug: e.target.value })} error={errors.departmentSlug}>
            {departments.map((d) => <option key={d.slug} value={d.slug}>{d.name}</option>)}
          </SelectField>
          <TextField
            label="Name"
            value={form.name}
            onChange={(e) => {
              const name = e.target.value;
              const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
              setForm({ ...form, name, slug });
            }}
            error={errors.name}
          />
          <TextField label="Slug" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} error={errors.slug} />
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
          <Button type="submit" isLoading={create.isPending}>Create category</Button>
        </div>
      </form>
    </Card>
  );
}

function NewAttribute({ category, onDone }: { category: AdminCategory; onDone: () => void }) {
  const add = useAddAttribute();
  const toast = useToast();
  const [form, setForm] = useState({ key: '', label: '', type: 'TEXT' as AttributeType, unit: '', filterable: true });
  const errors = fieldErrorsOf(add.error);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    add.mutate({ category: category.slug, ...form }, {
      onSuccess: () => {
        toast.show(`Specification ${form.label} added to ${category.name}.`);
        onDone();
      },
    });
  };
  return (
    <form onSubmit={submit} noValidate className="mt-4 space-y-3 border-t border-slate-100 pt-4">
      {add.error && <Alert tone="error">{errorMessage(add.error)}</Alert>}
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Label"
          value={form.label}
          onChange={(e) => {
            const label = e.target.value;
            setForm({ ...form, label, key: label.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/(^_|_$)/g, '') });
          }}
          error={errors.label}
        />
        <TextField label="Key" value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value })} error={errors.key} hint="Used by compatibility rules" />
        <SelectField label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as AttributeType })}>
          <option value="TEXT">Text</option>
          <option value="NUMBER">Number</option>
          <option value="BOOL">Yes / no</option>
          <option value="LIST">List of values</option>
        </SelectField>
        <TextField label="Unit" value={form.unit} onChange={(e) => setForm({ ...form, unit: e.target.value })} error={errors.unit} hint="Optional, e.g. cm or kg" />
      </div>
      <Checkbox label="Shoppers can filter by it" checked={form.filterable} onChange={(e) => setForm({ ...form, filterable: e.target.checked })} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onDone}>Cancel</Button>
        <Button type="submit" size="sm" isLoading={add.isPending}>Add specification</Button>
      </div>
    </form>
  );
}
