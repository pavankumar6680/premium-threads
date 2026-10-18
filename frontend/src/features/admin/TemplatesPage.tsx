import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import { formatMoney } from '../../lib/format';
import { useAdminCategories, useSaveTemplate, useTemplates, type AdminCategory, type AdminTemplate, type TemplateItem } from './api';
import { Badge, Card, Checkbox, errorMessage, fieldErrorsOf, PageHeader, splitList, TextAreaField } from './components/AdminUi';

export function TemplatesPage() {
  const templates = useTemplates();
  const categories = useAdminCategories();
  const [editing, setEditing] = useState<AdminTemplate | 'new' | null>(null);

  if (templates.isPending || categories.isPending) return <PageSpinner />;
  if (templates.isError || categories.isError) return <Alert tone="error">Templates could not be loaded.</Alert>;

  return (
    <>
      <PageHeader
        title="Goal templates"
        description="Templates power the goal wizard, the free-text interpreter (keywords) and the recommendation engine (items and usage tags). Existing goals keep the template they were created with."
        action={!editing && <Button onClick={() => setEditing('new')}><Plus className="size-4" aria-hidden="true" /> New template</Button>}
      />
      {editing && (
        <TemplateEditor
          key={editing === 'new' ? 'new' : editing.id}
          template={editing === 'new' ? null : editing}
          categories={categories.data}
          onDone={() => setEditing(null)}
        />
      )}
      <div className="grid gap-4 md:grid-cols-2">
        {templates.data.map((t) => (
          <div key={t.id} className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="font-semibold text-slate-900">{t.title}</h2>
                <p className="text-sm text-slate-600">{t.tagline}</p>
              </div>
              <div className="flex items-center gap-2">
                {t.active ? <Badge tone="green">Active</Badge> : <Badge>Hidden</Badge>}
                <Button variant="ghost" size="sm" onClick={() => setEditing(t)} aria-label={`Edit ${t.title}`}>
                  <Pencil className="size-4" aria-hidden="true" />
                </Button>
              </div>
            </div>
            <p className="mt-3 text-sm text-slate-700">
              Default budget {formatMoney(t.defaultBudget)} · {t.items.filter((i) => i.required).length} required,{' '}
              {t.items.filter((i) => !i.required).length} optional items
            </p>
            <p className="mt-1 truncate text-xs text-slate-500">Keywords: {t.keywords.join(', ') || 'none'}</p>
          </div>
        ))}
      </div>
    </>
  );
}

type Form = {
  slug: string;
  title: string;
  tagline: string;
  description: string;
  defaultBudget: string;
  contextTag: string;
  active: boolean;
  items: TemplateItem[];
  usage: string;
  keywords: string;
};

function TemplateEditor({ template, categories, onDone }: { template: AdminTemplate | null; categories: AdminCategory[]; onDone: () => void }) {
  const save = useSaveTemplate();
  const toast = useToast();
  const leaves = categories.filter((c) => c.leaf);
  const [form, setForm] = useState<Form>(() => ({
    slug: template?.slug ?? '',
    title: template?.title ?? '',
    tagline: template?.tagline ?? '',
    description: template?.description ?? '',
    defaultBudget: String(template?.defaultBudget ?? ''),
    contextTag: template?.contextTag ?? '',
    active: template?.active ?? true,
    items: template?.items.map((i) => ({ ...i })) ?? [{ categorySlug: '', required: true, selectedByDefault: true, quantity: 1, note: null }],
    usage: template?.usageOptions.map((u) => `${u.tag}: ${u.label}`).join('\n') ?? '',
    keywords: template?.keywords.join(', ') ?? '',
  }));
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }));
  const setItem = (index: number, changes: Partial<TemplateItem>) =>
    set('items', form.items.map((item, i) => (i === index ? { ...item, ...changes } : item)));
  const errors = fieldErrorsOf(save.error);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const usageOptions = form.usage
      .split('\n')
      .map((line) => line.split(':'))
      .filter(([tag]) => tag?.trim())
      .map(([tag, ...label]) => ({ tag: tag.trim().toLowerCase(), label: label.join(':').trim() || tag.trim() }));
    save.mutate(
      {
        id: template?.id ?? null,
        input: {
          slug: form.slug.trim(),
          title: form.title.trim(),
          tagline: form.tagline.trim(),
          description: form.description,
          defaultBudget: Number(form.defaultBudget),
          contextTag: form.contextTag.trim() || null,
          active: form.active,
          items: form.items.filter((i) => i.categorySlug).map(({ categoryName: _ignored, ...i }) => i),
          usageOptions,
          keywords: splitList(form.keywords),
        },
      },
      {
        onSuccess: () => {
          toast.show(template ? 'Template saved.' : 'Template created.');
          onDone();
        },
      },
    );
  };

  return (
    <Card title={template ? `Edit ${template.title}` : 'New template'} className="mb-6">
      <form onSubmit={submit} noValidate className="space-y-5">
        {save.error && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Title" value={form.title} onChange={(e) => set('title', e.target.value)} error={errors.title} />
          <TextField label="Slug" value={form.slug} onChange={(e) => set('slug', e.target.value)} error={errors.slug} hint="Used in links, e.g. home-office" />
          <TextField label="Tagline" className="sm:col-span-2" value={form.tagline} onChange={(e) => set('tagline', e.target.value)} error={errors.tagline} />
          <TextAreaField label="Description" className="sm:col-span-2" rows={3} value={form.description} onChange={(e) => set('description', e.target.value)} />
          <TextField label="Default budget (USD)" type="number" min={1} value={form.defaultBudget} onChange={(e) => set('defaultBudget', e.target.value)} error={errors.defaultBudget} />
          <TextField label="Context tag" value={form.contextTag} onChange={(e) => set('contextTag', e.target.value)} error={errors.contextTag} hint="Product tag that marks items made for this purpose, e.g. gaming" />
        </div>

        <fieldset>
          <legend className="mb-2 text-sm font-semibold text-slate-900">Items</legend>
          {errors.items && <p className="mb-2 text-sm text-red-600">{errors.items}</p>}
          <div className="space-y-2">
            {form.items.map((item, index) => (
              <div key={index} className="flex flex-wrap items-center gap-3 rounded-xl bg-slate-50 p-3">
                <select
                  aria-label={`Item ${index + 1} category`}
                  value={item.categorySlug}
                  onChange={(e) => setItem(index, { categorySlug: e.target.value })}
                  className="h-10 min-w-48 flex-1 rounded-lg border border-slate-300 bg-white px-2 text-sm"
                >
                  <option value="">Choose a category</option>
                  {leaves.map((c) => <option key={c.slug} value={c.slug}>{c.departmentName} · {c.name}</option>)}
                </select>
                <Checkbox label="Required" checked={item.required} onChange={(e) => setItem(index, { required: e.target.checked, selectedByDefault: e.target.checked || item.selectedByDefault })} />
                <Checkbox label="Selected by default" checked={item.selectedByDefault} disabled={item.required} onChange={(e) => setItem(index, { selectedByDefault: e.target.checked })} />
                <label className="flex items-center gap-1.5 text-sm text-slate-700">
                  Qty
                  <input type="number" min={1} max={10} value={item.quantity} onChange={(e) => setItem(index, { quantity: Number(e.target.value) })} className="h-9 w-16 rounded-lg border border-slate-300 px-2" />
                </label>
                <Button type="button" variant="ghost" size="sm" aria-label={`Remove item ${index + 1}`} onClick={() => set('items', form.items.filter((_, i) => i !== index))}>
                  <Trash2 className="size-4" aria-hidden="true" />
                </Button>
              </div>
            ))}
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="mt-2"
            onClick={() => set('items', [...form.items, { categorySlug: '', required: false, selectedByDefault: true, quantity: 1, note: null }])}
          >
            <Plus className="size-4" aria-hidden="true" /> Add item
          </Button>
        </fieldset>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextAreaField label="Usage options" rows={4} value={form.usage} onChange={(e) => set('usage', e.target.value)} hint="One per line as tag: label, e.g. streaming: I stream" />
          <TextAreaField label="Keywords" rows={4} value={form.keywords} onChange={(e) => set('keywords', e.target.value)} hint="Comma-separated phrases that identify this goal in free text" />
        </div>
        <Checkbox label="Active (shown to shoppers)" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
          <Button type="submit" isLoading={save.isPending}>{template ? 'Save template' : 'Create template'}</Button>
        </div>
      </form>
    </Card>
  );
}
