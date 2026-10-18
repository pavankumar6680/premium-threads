import clsx from 'clsx';
import { FlaskConical, Pencil, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/Toast';
import {
  useAdminCategories,
  usePreviewRule,
  useRules,
  useSaveRule,
  useSetRuleActive,
  type AdminCategory,
  type AdminRule,
  type Operator,
  type RuleInput,
} from './api';
import { Badge, Card, Checkbox, errorMessage, fieldErrorsOf, PageHeader, SelectField, TableWrap, td, th, Toggle } from './components/AdminUi';

const OPERATORS: Record<Operator, { label: string; help: string; pairwise: boolean; space: boolean }> = {
  EQUALS: { label: 'Values match', help: 'A value of the source equals a value of the target (lens mount = camera mount).', pairwise: true, space: false },
  LIST_CONTAINS: { label: 'Target supports all', help: 'The target list contains every source value (card format in the camera’s slots).', pairwise: true, space: false },
  LIST_INTERSECTS: { label: 'Share a value', help: 'Source and target lists share at least one value (both made for the same wardrobe).', pairwise: true, space: false },
  LIST_DISJOINT: { label: 'No shared value', help: 'Source and target lists have no value in common (two pieces never cover the same part of an outfit).', pairwise: true, space: false },
  LTE: { label: 'At most', help: 'Source number ≤ target number.', pairwise: true, space: false },
  MAX_DIFFERENCE: { label: 'Close in value', help: 'The two numbers differ by at most the given amount (dress codes at most 2 levels apart).', pairwise: true, space: false },
  LTE_SPACE_WIDTH: { label: 'Fits room width', help: 'Source number ≤ the width the customer entered.', pairwise: false, space: true },
  LTE_SPACE_DEPTH: { label: 'Fits room depth', help: 'Source number ≤ the depth the customer entered.', pairwise: false, space: true },
  REQUIRES_CATEGORY_UNLESS: { label: 'Needs a companion', help: 'Unless the source list contains the value, the bundle needs a product from the target category (an XLR-only mic needs an audio interface).', pairwise: false, space: false },
};

/** Rule category meaning "every category". */
const ANY = '*';

const DERIVED_ATTRIBUTES = [
  { key: 'wardrobe', label: 'Wardrobe (derived)' },
  { key: 'covers', label: 'Part of the outfit (derived)' },
  { key: 'dress_code', label: 'Dress code (derived)' },
];

function uniqueAttributes(categories: AdminCategory[]) {
  const seen = new Map<string, AdminCategory['attributes'][number]>();
  categories.flatMap((c) => c.attributes).forEach((a) => seen.set(a.key, seen.get(a.key) ?? a));
  return [...seen.values()];
}

const EMPTY: RuleInput = {
  code: '',
  sourceCategory: '',
  targetCategory: null,
  operator: 'LIST_INTERSECTS',
  sourceAttr: '',
  targetAttr: null,
  value: null,
  severity: 'BLOCK',
  label: '',
  message: '',
  active: true,
};

export function RulesPage() {
  const rules = useRules();
  const categories = useAdminCategories();
  const setActive = useSetRuleActive();
  const toast = useToast();
  const [editing, setEditing] = useState<AdminRule | 'new' | null>(null);

  if (rules.isPending || categories.isPending) return <PageSpinner />;
  if (rules.isError || categories.isError) return <Alert tone="error">Rules could not be loaded.</Alert>;
  const name = (slug: string | null) => (slug === ANY ? 'Any piece' : categories.data.find((c) => c.slug === slug)?.name ?? slug ?? '');

  return (
    <>
      <PageHeader
        title="Compatibility rules"
        description="Rules are data: changes apply to the next compatibility check and recommendation, with no deployment. Preview a rule to see how much of the catalog it affects."
        action={
          !editing && (
            <Button onClick={() => setEditing('new')}>
              <Plus className="size-4" aria-hidden="true" /> New rule
            </Button>
          )
        }
      />
      {editing && (
        <RuleEditor
          key={editing === 'new' ? 'new' : editing.id}
          rule={editing === 'new' ? null : editing}
          categories={categories.data}
          onDone={() => setEditing(null)}
        />
      )}
      <TableWrap>
        <thead className="bg-slate-50">
          <tr>
            <th className={th}>Rule</th>
            <th className={th}>Applies to</th>
            <th className={th}>Check</th>
            <th className={th}>Severity</th>
            <th className={th}>Active</th>
            <th className={th}><span className="sr-only">Edit</span></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rules.data.map((r) => (
            <tr key={r.id} className={clsx(!r.active && 'text-slate-400')}>
              <td className={td}>
                <p className="font-medium text-slate-900">{r.label}</p>
                <p className="font-mono text-xs text-slate-500">{r.code}</p>
              </td>
              <td className={clsx(td, 'whitespace-nowrap')}>
                {name(r.sourceCategory)}
                {r.targetCategory && <> → {name(r.targetCategory)}</>}
              </td>
              <td className={td}>
                <span className="whitespace-nowrap">{OPERATORS[r.operator].label}</span>
                <p className="font-mono text-xs text-slate-500">
                  {r.sourceAttr}{r.targetAttr && ` · ${r.targetAttr}`}{r.value && ` · unless ${r.value}`}
                </p>
              </td>
              <td className={td}>{r.severity === 'BLOCK' ? <Badge tone="red">Block</Badge> : <Badge tone="amber">Warn</Badge>}</td>
              <td className={td}>
                <Toggle
                  checked={r.active}
                  label={`${r.code} active`}
                  disabled={setActive.isPending}
                  onChange={(active) =>
                    setActive.mutate(
                      { id: r.id, active },
                      { onSuccess: () => toast.show(active ? `${r.code} is active.` : `${r.code} is off.`) },
                    )
                  }
                />
              </td>
              <td className={td}>
                <Button variant="ghost" size="sm" onClick={() => setEditing(r)} aria-label={`Edit ${r.code}`}>
                  <Pencil className="size-4" aria-hidden="true" />
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
    </>
  );
}

function RuleEditor({ rule, categories, onDone }: { rule: AdminRule | null; categories: AdminCategory[]; onDone: () => void }) {
  const [form, setForm] = useState<RuleInput>(() => (rule ? { ...rule } : EMPTY));
  const save = useSaveRule();
  const preview = usePreviewRule();
  const toast = useToast();
  const op = OPERATORS[form.operator];
  const needsTarget = op.pairwise || form.operator === 'REQUIRES_CATEGORY_UNLESS';
  const leaves = categories.filter((c) => c.leaf && c.attributes.length > 0);
  // every product also carries attributes derived by the catalog, usable in any rule
  const derived = DERIVED_ATTRIBUTES.map((a) => ({ ...a, type: 'LIST' as const, unit: null, filterable: false }));
  const attrs = (slug: string | null) => {
    if (!slug) return [];
    const own = slug === ANY ? uniqueAttributes(leaves) : categories.find((c) => c.slug === slug)?.attributes ?? [];
    return [...derived, ...own.filter((a) => !derived.some((d) => d.key === a.key))];
  };
  const errors = { ...fieldErrorsOf(preview.error), ...fieldErrorsOf(save.error) };
  const set = <K extends keyof RuleInput>(key: K, value: RuleInput[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    preview.reset();
  };

  const input = (): RuleInput => ({
    ...form,
    code: form.code.trim().toUpperCase(),
    targetCategory: needsTarget ? form.targetCategory : null,
    targetAttr: op.pairwise ? form.targetAttr : null,
    value: form.operator === 'REQUIRES_CATEGORY_UNLESS' || form.operator === 'MAX_DIFFERENCE' ? form.value : null,
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(
      { id: rule?.id ?? null, input: input() },
      {
        onSuccess: () => {
          toast.show(rule ? 'Rule saved.' : 'Rule created.');
          onDone();
        },
      },
    );
  };

  return (
    <Card title={rule ? `Edit ${rule.code}` : 'New rule'} className="mb-6">
      <form onSubmit={submit} noValidate className="space-y-4">
        {(save.error || preview.error) && <Alert tone="error">{errorMessage(save.error ?? preview.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Code" value={form.code} onChange={(e) => set('code', e.target.value)} error={errors.code} hint="UPPER_SNAKE_CASE, e.g. MIC_NEEDS_INTERFACE" />
          <SelectField label="Kind of check" value={form.operator} onChange={(e) => set('operator', e.target.value as Operator)} hint={op.help}>
            {(Object.keys(OPERATORS) as Operator[]).map((o) => <option key={o} value={o}>{OPERATORS[o].label}</option>)}
          </SelectField>
          <SelectField label="Source category" value={form.sourceCategory} onChange={(e) => setForm((f) => ({ ...f, sourceCategory: e.target.value, sourceAttr: '' }))} error={errors.sourceCategory}>
            <option value="">Choose…</option>
            {op.pairwise && <option value={ANY}>Any piece</option>}
            {leaves.map((c) => <option key={c.slug} value={c.slug}>{c.name}</option>)}
          </SelectField>
          <SelectField label="Source attribute" value={form.sourceAttr} onChange={(e) => set('sourceAttr', e.target.value)} error={errors.sourceAttr} disabled={!form.sourceCategory}>
            <option value="">Choose…</option>
            {attrs(form.sourceCategory).map((a) => <option key={a.key} value={a.key}>{a.label} ({a.key})</option>)}
          </SelectField>
          {needsTarget && (
            <SelectField label="Target category" value={form.targetCategory ?? ''} onChange={(e) => setForm((f) => ({ ...f, targetCategory: e.target.value || null, targetAttr: null }))} error={errors.targetCategory}>
              <option value="">Choose…</option>
              {op.pairwise && <option value={ANY}>Any piece</option>}
              {leaves.map((c) => <option key={c.slug} value={c.slug}>{c.name}</option>)}
            </SelectField>
          )}
          {op.pairwise && (
            <SelectField label="Target attribute" value={form.targetAttr ?? ''} onChange={(e) => set('targetAttr', e.target.value || null)} error={errors.targetAttr} disabled={!form.targetCategory}>
              <option value="">Choose…</option>
              {attrs(form.targetCategory).map((a) => <option key={a.key} value={a.key}>{a.label} ({a.key})</option>)}
            </SelectField>
          )}
          {form.operator === 'MAX_DIFFERENCE' && (
            <TextField label="Largest difference" type="number" min={0} step="any" value={form.value ?? ''} onChange={(e) => set('value', e.target.value)} error={errors.value} hint="e.g. 2" />
          )}
          {form.operator === 'REQUIRES_CATEGORY_UNLESS' && (
            <TextField label="Unless the source has" value={form.value ?? ''} onChange={(e) => set('value', e.target.value)} error={errors.value} hint="e.g. USB" />
          )}
          <SelectField label="Severity" value={form.severity} onChange={(e) => set('severity', e.target.value as RuleInput['severity'])} hint="Block: never recommended or sold as a compatible bundle. Warn: shown as a warning.">
            <option value="BLOCK">Block</option>
            <option value="WARN">Warn</option>
          </SelectField>
          <TextField label="Label when satisfied" value={form.label} onChange={(e) => set('label', e.target.value)} error={errors.label} hint="Shown in explanations, e.g. “within the arm’s load limit”" />
          <TextField label="Message when violated" value={form.message} onChange={(e) => set('message', e.target.value)} error={errors.message} />
        </div>
        <Checkbox label="Active" checked={form.active} onChange={(e) => set('active', e.target.checked)} />

        {preview.data && (
          <div className={clsx('rounded-xl p-4 text-sm', preview.data.violating > 0 ? 'bg-amber-50 text-amber-900' : 'bg-emerald-50 text-emerald-900')} role="status">
            <p className="font-medium">{preview.data.summary}</p>
            {preview.data.examples.length > 0 && (
              <ul className="mt-2 list-disc space-y-0.5 pl-5">
                {preview.data.examples.map((ex) => <li key={ex}>{ex}</li>)}
              </ul>
            )}
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
          <Button type="button" variant="secondary" onClick={() => preview.mutate({ ...input(), label: form.label.trim() || 'preview', message: form.message.trim() || 'preview' })} isLoading={preview.isPending}>
            <FlaskConical className="size-4" aria-hidden="true" /> Preview impact
          </Button>
          <Button type="submit" isLoading={save.isPending}>{rule ? 'Save rule' : 'Create rule'}</Button>
        </div>
      </form>
    </Card>
  );
}
