import clsx from 'clsx';
import { ExternalLink, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import { useDeleteHomeSection, useHomeSections, useSaveHomeSection, type HomeSection } from './api';
import { Badge, Card, errorMessage, fieldErrorsOf, PageHeader, TableWrap, td, th, Toggle } from './components/AdminUi';

const EXAMPLES = ['flag=trending', 'flag=sale&sort=discount', 'style=streetwear&gender=WOMEN', 'brand=levis', 'occasion=office'];

/**
 * The homepage's product rails. Built-in rails keep their place in the layout (titles and filters can change,
 * and they can be hidden); extra collections appear after them, in order.
 */
export function HomepageAdminPage() {
  const sections = useHomeSections();
  const save = useSaveHomeSection();
  const remove = useDeleteHomeSection();
  const toast = useToast();
  const [editing, setEditing] = useState<HomeSection | 'new' | null>(null);

  if (sections.isPending) return <PageSpinner />;
  if (sections.isError) return <Alert tone="error">Homepage sections could not be loaded.</Alert>;

  return (
    <>
      <PageHeader
        title="Homepage"
        description="Product rails on the homepage. Each one shows products matching a catalog filter; featured brands are chosen on the Brands page."
        action={
          <div className="flex items-center gap-3">
            <Link to="/" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
              View homepage <ExternalLink className="size-4" aria-hidden="true" />
            </Link>
            {!editing && <Button onClick={() => setEditing('new')}><Plus className="size-4" aria-hidden="true" /> New collection</Button>}
          </div>
        }
      />
      {editing && (
        <div className="mb-4">
          <SectionEditor key={editing === 'new' ? 'new' : editing.id} section={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />
        </div>
      )}
      <TableWrap>
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className={th}>Order</th>
              <th className={th}>Section</th>
              <th className={th}>Products shown</th>
              <th className={th}>Visible</th>
              <th className={th}><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {sections.data.map((s) => (
              <tr key={s.id} className={clsx(!s.active && 'text-slate-400')}>
                <td className={td}>{s.position}</td>
                <td className={td}>
                  <p className="text-xs uppercase tracking-wide text-slate-500">{s.eyebrow}</p>
                  <p className="font-medium text-slate-900">{s.title} {s.builtIn && <Badge>Built-in</Badge>}</p>
                </td>
                <td className={td}>
                  <Link to={`/shop?${s.query}`} className="font-mono text-xs hover:underline">{s.query}</Link>
                </td>
                <td className={td}>
                  <Toggle
                    checked={s.active}
                    label={`${s.title} visible`}
                    disabled={save.isPending}
                    onChange={(active) =>
                      save.mutate(
                        { id: s.id, input: { eyebrow: s.eyebrow, title: s.title, query: s.query, position: s.position, active } },
                        { onError: (e) => toast.show(errorMessage(e) ?? 'Could not update', 'error') },
                      )
                    }
                  />
                </td>
                <td className={clsx(td, 'whitespace-nowrap')}>
                  <button type="button" className="text-sm font-semibold text-brand-700 hover:underline" onClick={() => setEditing(s)}>Edit</button>
                  {!s.builtIn && (
                    <button
                      type="button"
                      className="ml-3 text-sm font-semibold text-red-600 hover:underline"
                      onClick={() => window.confirm(`Delete “${s.title}”?`) && remove.mutate(s.id, { onSuccess: () => toast.show('Collection deleted.') })}
                    >
                      Delete
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableWrap>
    </>
  );
}

function SectionEditor({ section, onDone }: { section: HomeSection | null; onDone: () => void }) {
  const save = useSaveHomeSection();
  const toast = useToast();
  const [form, setForm] = useState({
    eyebrow: section?.eyebrow ?? '',
    title: section?.title ?? '',
    query: section?.query ?? '',
    position: section ? String(section.position) : '',
  });
  const errors = fieldErrorsOf(save.error);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(
      {
        id: section?.id ?? null,
        input: {
          eyebrow: form.eyebrow.trim(),
          title: form.title.trim(),
          query: form.query.trim(),
          ...(form.position.trim() && { position: Number(form.position) }),
          active: section?.active ?? true,
        },
      },
      {
        onSuccess: () => {
          toast.show(section ? 'Section saved.' : 'Collection added to the homepage.');
          onDone();
        },
      },
    );
  };

  return (
    <Card title={section ? `Edit ${section.title}` : 'New collection'}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {save.error && !errors.query && !errors.title && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Eyebrow" value={form.eyebrow} onChange={(e) => setForm({ ...form, eyebrow: e.target.value })} hint="Small text above the title, e.g. Just landed" />
          <TextField label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} error={errors.title} required />
          <TextField
            label="Catalog filter"
            value={form.query}
            onChange={(e) => setForm({ ...form, query: e.target.value })}
            error={errors.query}
            hint={`The same filters as the shop page, e.g. ${EXAMPLES.join(' · ')}`}
            required
          />
          <TextField label="Order" type="number" min={0} value={form.position} onChange={(e) => setForm({ ...form, position: e.target.value })} />
        </div>
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
          <Button type="submit" isLoading={save.isPending}>{section ? 'Save section' : 'Add collection'}</Button>
        </div>
      </form>
    </Card>
  );
}
