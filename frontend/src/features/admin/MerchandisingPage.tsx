import clsx from 'clsx';
import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import { useSaveTerm, useTerms, type AdminTerm, type TermKind } from './api';
import { Card, errorMessage, fieldErrorsOf, PageHeader, TableWrap, td, th, Toggle } from './components/AdminUi';

const LABELS: Record<TermKind, { title: string; singular: string; help: string }> = {
  styles: { title: 'Styles', singular: 'style', help: 'Shop-by-style tiles, filters and the outfit builder use these.' },
  occasions: { title: 'Occasions', singular: 'occasion', help: 'Products are tagged with the occasions they suit (office, party, travel…).' },
};

/** Styles and occasions: the vocabulary of the store's filters and outfit builder. */
export function MerchandisingPage() {
  const [kind, setKind] = useState<TermKind>('styles');
  return (
    <>
      <PageHeader title="Styles & occasions" description="Add, rename, reorder or hide them without code changes. Slugs never change, so links keep working." />
      <div className="mb-4 inline-flex rounded-xl bg-slate-100 p-1" role="tablist" aria-label="Vocabulary">
        {(Object.keys(LABELS) as TermKind[]).map((k) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={kind === k}
            onClick={() => setKind(k)}
            className={clsx('rounded-lg px-4 py-1.5 text-sm font-semibold', kind === k ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600')}
          >
            {LABELS[k].title}
          </button>
        ))}
      </div>
      <TermList key={kind} kind={kind} />
    </>
  );
}

function TermList({ kind }: { kind: TermKind }) {
  const terms = useTerms(kind);
  const save = useSaveTerm();
  const toast = useToast();
  const [editing, setEditing] = useState<AdminTerm | 'new' | null>(null);
  const label = LABELS[kind];

  if (terms.isPending) return <PageSpinner />;
  if (terms.isError) return <Alert tone="error">Could not load {label.title.toLowerCase()}.</Alert>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-600">{label.help}</p>
        {!editing && <Button onClick={() => setEditing('new')}><Plus className="size-4" aria-hidden="true" /> New {label.singular}</Button>}
      </div>
      {editing && <TermEditor kind={kind} term={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      <TableWrap>
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className={th}>Name</th>
              <th className={th}>Order</th>
              <th className={th}>Products</th>
              <th className={th}>Visible</th>
              <th className={th}><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {terms.data.map((t) => (
              <tr key={t.id} className={clsx(!t.active && 'text-slate-400')}>
                <td className={td}>
                  <p className="font-medium text-slate-900">{t.name}</p>
                  <p className="text-xs text-slate-500">{t.description || <span className="font-mono">{t.slug}</span>}</p>
                </td>
                <td className={td}>{t.sortOrder}</td>
                <td className={td}>{t.productCount}</td>
                <td className={td}>
                  <Toggle
                    checked={t.active}
                    label={`${t.name} visible`}
                    disabled={save.isPending}
                    onChange={(active) =>
                      save.mutate(
                        { kind, id: t.id, input: { name: t.name, description: t.description, sortOrder: t.sortOrder, active } },
                        { onError: (e) => toast.show(errorMessage(e) ?? 'Could not update', 'error') },
                      )
                    }
                  />
                </td>
                <td className={td}>
                  <button type="button" className="text-sm font-semibold text-brand-700 hover:underline" onClick={() => setEditing(t)}>Edit</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableWrap>
    </div>
  );
}

function TermEditor({ kind, term, onDone }: { kind: TermKind; term: AdminTerm | null; onDone: () => void }) {
  const save = useSaveTerm();
  const toast = useToast();
  const [form, setForm] = useState({ name: term?.name ?? '', description: term?.description ?? '', sortOrder: term ? String(term.sortOrder) : '' });
  const errors = fieldErrorsOf(save.error);
  const singular = LABELS[kind].singular;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(
      {
        kind,
        id: term?.id ?? null,
        input: {
          name: form.name.trim(),
          description: form.description.trim(),
          ...(form.sortOrder.trim() && { sortOrder: Number(form.sortOrder) }),
          active: term?.active ?? true,
        },
      },
      {
        onSuccess: () => {
          toast.show(term ? `${form.name} saved.` : `${form.name} added.`);
          onDone();
        },
      },
    );
  };

  return (
    <Card title={term ? `Edit ${term.name}` : `New ${singular}`}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {save.error && !errors.name && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-[1fr_2fr_120px]">
          <TextField label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} error={errors.name} required />
          <TextField label="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} error={errors.description} />
          <TextField label="Order" type="number" min={0} value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} error={errors.sortOrder} />
        </div>
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
          <Button type="submit" isLoading={save.isPending}>{term ? 'Save' : `Add ${singular}`}</Button>
        </div>
      </form>
    </Card>
  );
}
