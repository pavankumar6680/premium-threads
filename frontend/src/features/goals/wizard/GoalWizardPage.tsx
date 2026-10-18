import { zodResolver } from '@hookform/resolvers/zod';
import clsx from 'clsx';
import { Check, ChevronLeft, ChevronRight, Sparkles, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Controller, useFieldArray, useForm, useWatch, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { Alert } from '../../../components/ui/Alert';
import { Button } from '../../../components/ui/Button';
import { QuantityStepper } from '../../../components/ui/QuantityStepper';
import { PageSpinner } from '../../../components/ui/Spinner';
import { TextField } from '../../../components/ui/TextField';
import { ApiError } from '../../../lib/api';
import { formatMoney } from '../../../lib/format';
import { categoryVisual } from '../../catalog/categoryVisuals';
import {
  generalUsageOptions,
  kindLabels,
  qualityLabels,
  SPACE_SENSITIVE_CATEGORIES,
  useCreateGoal,
  useGoal,
  useGoalCategories,
  useGoalTemplate,
  type CategoryOption,
  type GoalAnalysis,
  type Quality,
  type TemplateDetail,
} from '../api';
import { blank, estimate, fromAnalysis, fromGoal, fromTemplate, stepFields, toCreateInput, wizardSchema, type ItemKind, type WizardValues } from './wizardModel';

const STEPS = ['What you need', 'Budget', 'Preferences', 'Review'];
const KINDS: ItemKind[] = ['REQUIRED', 'OPTIONAL', 'OWNED', 'SKIP'];
const MAX_BRANDS = 10;

type AnalysisState = { analysis: GoalAnalysis; text: string } | null;

/** Loads whatever the wizard starts from (template, interpreted text or an existing goal), then renders it. */
export function GoalWizardPage() {
  const [params] = useSearchParams();
  const location = useLocation();
  const analysisState = (location.state as AnalysisState) ?? null;
  const fromId = params.get('from');

  const categories = useGoalCategories();
  const source = useGoal(fromId);
  const templateSlug = params.get('template') ?? analysisState?.analysis.templateSlug ?? source.data?.template?.slug ?? null;
  const template = useGoalTemplate(templateSlug);

  const loading = categories.isPending || (!!fromId && source.isPending) || (!!templateSlug && template.isPending);
  if (loading) return <PageSpinner />;
  if (categories.isError || template.isError || source.isError) {
    const message = (template.error ?? source.error ?? categories.error)?.message;
    return <div className="mx-auto max-w-3xl px-4 py-10"><Alert tone="error">{message ?? 'Could not load the goal builder.'}</Alert></div>;
  }

  const initial = source.data
    ? fromGoal(source.data, template.data)
    : analysisState
      ? fromAnalysis(analysisState.analysis, analysisState.text, template.data)
      : template.data
        ? fromTemplate(template.data)
        : blank();

  return (
    <GoalWizard
      key={`${templateSlug}-${fromId}-${analysisState?.text}`}
      initial={initial}
      template={template.data}
      categories={categories.data}
      analysis={analysisState?.analysis}
    />
  );
}

function GoalWizard({ initial, template, categories, analysis }: {
  initial: WizardValues;
  template: TemplateDetail | undefined;
  categories: CategoryOption[];
  analysis: GoalAnalysis | undefined;
}) {
  const navigate = useNavigate();
  const createGoal = useCreateGoal();
  const [step, setStep] = useState(0);
  const byslug = useMemo(() => new Map(categories.map((c) => [c.slug, c])), [categories]);

  const form = useForm({ resolver: zodResolver(wizardSchema), defaultValues: initial, mode: 'onTouched' });
  const { control, register, handleSubmit, trigger, formState: { errors } } = form;
  const items = useWatch({ control, name: 'items' });
  const budget = useWatch({ control, name: 'budget' });
  const cost = estimate(items, byslug);

  const next = async () => {
    if (await trigger(stepFields[step])) {
      setStep((s) => Math.min(s + 1, STEPS.length - 1));
      window.scrollTo({ top: 0 });
    }
  };

  const submit = handleSubmit(async (values) => {
    const goal = await createGoal.mutateAsync(toCreateInput(values)).catch(() => null);
    if (goal) navigate(`/goals/${goal.id}`);
  });

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <p className="text-sm font-semibold text-brand-700">{template ? template.title : 'Custom goal'}</p>
      <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Build your goal</h1>

      <ol className="mt-6 grid grid-cols-4 gap-2" aria-label="Steps">
        {STEPS.map((label, i) => (
          <li key={label} aria-current={i === step ? 'step' : undefined}>
            <div className={clsx('h-1.5 rounded-full', i <= step ? 'bg-brand-600' : 'bg-slate-200')} />
            <p className={clsx('mt-2 text-xs font-medium sm:text-sm', i === step ? 'text-brand-800' : 'text-slate-500')}>{label}</p>
          </li>
        ))}
      </ol>

      {analysis && step === 0 && <AnalysisBanner analysis={analysis} />}

      <form onSubmit={submit} noValidate className="mt-6">
        {step === 0 && <ItemsStep control={control} register={register} template={template} categories={categories} byslug={byslug} error={errors.items?.root?.message ?? errors.items?.message} />}
        {step === 1 && <BudgetStep control={control} register={register} budget={budget} cost={cost} error={errors.budget?.message} />}
        {step === 2 && <PreferencesStep control={control} register={register} template={template} byslug={byslug} errors={errors} />}
        {step === 3 && <ReviewStep values={form.getValues()} byslug={byslug} cost={cost} />}

        {createGoal.isError && (
          <div className="mt-4">
            <Alert tone="error">{createGoal.error instanceof ApiError ? createGoal.error.message : 'Could not save your goal.'}</Alert>
          </div>
        )}

        <div className="mt-8 flex items-center justify-between border-t border-slate-200 pt-6">
          <Button type="button" variant="ghost" onClick={() => setStep((s) => s - 1)} disabled={step === 0}>
            <ChevronLeft className="size-4" aria-hidden="true" /> Back
          </Button>
          {/* Distinct keys: if React reused one <button> and switched its type to "submit" while the Continue
              click is still being dispatched, the browser would submit the form and skip the review step. */}
          {step < STEPS.length - 1 ? (
            <Button key="continue" type="button" onClick={next}>
              Continue <ChevronRight className="size-4" aria-hidden="true" />
            </Button>
          ) : (
            <Button key="create" type="submit" size="lg" isLoading={createGoal.isPending}>
              <Check className="size-5" aria-hidden="true" /> Create my goal
            </Button>
          )}
        </div>
      </form>
    </div>
  );
}

function AnalysisBanner({ analysis }: { analysis: GoalAnalysis }) {
  return (
    <div className="mt-6 space-y-3 rounded-2xl border border-brand-200 bg-brand-50 p-4">
      <p className="flex items-center gap-2 text-sm font-semibold text-brand-900">
        <Sparkles className="size-4" aria-hidden="true" />
        {analysis.source === 'AI'
          ? 'We pre-filled this from your description using AI, checked against our catalog. Review and adjust anything.'
          : 'We pre-filled this from your description. Review and adjust anything.'}
      </p>
      {analysis.matchedTerms.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Recognised terms">
          {analysis.matchedTerms.map((term) => (
            <li key={term} className="rounded-full bg-white px-2.5 py-0.5 text-xs font-medium text-brand-800 ring-1 ring-brand-200">{term}</li>
          ))}
        </ul>
      )}
      {analysis.clarifications.map((q) => (
        <Alert key={q} tone="info">{q}</Alert>
      ))}
    </div>
  );
}

type StepProps = { control: Control<WizardValues>; register: UseFormRegister<WizardValues> };

function priceRange(c: CategoryOption | undefined) {
  if (!c || c.minPrice === null || c.maxPrice === null) return 'Out of stock';
  return c.minPrice === c.maxPrice ? formatMoney(c.minPrice) : `${formatMoney(c.minPrice)} – ${formatMoney(c.maxPrice)}`;
}

function ItemsStep({ control, register, template, categories, byslug, error }: StepProps & {
  template: TemplateDetail | undefined;
  categories: CategoryOption[];
  byslug: Map<string, CategoryOption>;
  error?: string;
}) {
  const { fields, append, remove } = useFieldArray({ control, name: 'items' });
  const items = useWatch({ control, name: 'items' });
  const templateSlugs = new Set(template?.items.map((i) => i.category.slug));
  const notes = new Map(template?.items.map((i) => [i.category.slug, i.note]));
  const used = new Set(items.map((i) => i.categorySlug));
  const departments = [...new Set(categories.map((c) => c.departmentName))];

  return (
    <section aria-labelledby="items-heading">
      <h2 id="items-heading" className="text-lg font-semibold">What do you need?</h2>
      <p className="mt-1 text-sm text-slate-600">Mark what to buy, what would be nice, and what you already own, so we don’t replace it.</p>
      {error && <div className="mt-3"><Alert tone="error">{error}</Alert></div>}

      <ul className="mt-4 space-y-3">
        {fields.map((field, index) => {
          const category = byslug.get(field.categorySlug);
          const kind = items[index]?.kind;
          const { icon: Icon, tile } = categoryVisual(field.categorySlug);
          const buying = kind === 'REQUIRED' || kind === 'OPTIONAL';
          return (
            <li key={field.id} className={clsx('rounded-2xl border bg-white p-4', kind === 'SKIP' ? 'border-slate-200 opacity-70' : 'border-slate-300')}>
              <div className="flex flex-wrap items-start gap-3">
                <span className={clsx('flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br', tile)}>
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{category?.name ?? field.categorySlug}</p>
                  <p className="text-xs text-slate-500">
                    {priceRange(category)}
                    {category && category.inStockCount > 0 && ` · ${category.inStockCount} in stock`}
                  </p>
                  {notes.get(field.categorySlug) && <p className="mt-1 text-xs text-slate-600">{notes.get(field.categorySlug)}</p>}
                </div>
                {!templateSlugs.has(field.categorySlug) && (
                  <button type="button" onClick={() => remove(index)} className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600" aria-label={`Remove ${category?.name}`}>
                    <Trash2 className="size-4" aria-hidden="true" />
                  </button>
                )}
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-3">
                <div role="radiogroup" aria-label={`${category?.name ?? field.categorySlug}: what do you need?`} className="inline-flex flex-wrap rounded-lg bg-slate-100 p-1">
                  {KINDS.map((k) => (
                    <label key={k} className="cursor-pointer">
                      <input type="radio" value={k} className="peer sr-only" {...register(`items.${index}.kind`)} />
                      <span className="block rounded-md px-3 py-1.5 text-xs font-semibold text-slate-600 peer-checked:bg-white peer-checked:text-brand-800 peer-checked:shadow-sm peer-focus-visible:ring-2 peer-focus-visible:ring-brand-500">
                        {kindLabels[k]}
                      </span>
                    </label>
                  ))}
                </div>
                {buying && (
                  <Controller
                    control={control}
                    name={`items.${index}.quantity`}
                    render={({ field: f }) => (
                      <QuantityStepper value={f.value} max={10} onChange={f.onChange} label={`Quantity of ${category?.name}`} />
                    )}
                  />
                )}
              </div>
            </li>
          );
        })}
      </ul>

      <label className="mt-4 flex flex-wrap items-center gap-2 text-sm">
        <span className="font-medium text-slate-700">Add something else:</span>
        <select
          className="h-10 rounded-lg border border-slate-300 bg-white px-2 text-sm"
          value=""
          onChange={(e) => e.target.value && append({ categorySlug: e.target.value, kind: 'REQUIRED', quantity: 1 })}
          aria-label="Add a category"
        >
          <option value="">Choose a category…</option>
          {departments.map((dept) => (
            <optgroup key={dept} label={dept}>
              {categories.filter((c) => c.departmentName === dept && !used.has(c.slug)).map((c) => (
                <option key={c.slug} value={c.slug}>{c.name}</option>
              ))}
            </optgroup>
          ))}
        </select>
      </label>
    </section>
  );
}

function BudgetStep({ control, register, budget, cost, error }: StepProps & {
  budget: number;
  cost: ReturnType<typeof estimate>;
  error?: string;
}) {
  const quality = useWatch({ control, name: 'quality' });
  const validBudget = Number.isFinite(budget) && budget > 0;
  const fitsEssentials = validBudget && cost.essentials <= budget;
  const usage = validBudget ? Math.min(100, Math.round((cost.essentials / budget) * 100)) : 0;

  return (
    <section aria-labelledby="budget-heading" className="space-y-6">
      <div>
        <h2 id="budget-heading" className="text-lg font-semibold">What’s your budget?</h2>
        <div className="mt-3 max-w-xs">
          <label htmlFor="budget" className="mb-1.5 block text-sm font-medium text-slate-700">Total budget (USD)</label>
          <div className="relative">
            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500">$</span>
            <input
              id="budget"
              type="number"
              inputMode="decimal"
              min={1}
              step="0.01"
              aria-invalid={error ? true : undefined}
              className={clsx('h-12 w-full rounded-lg border bg-white pl-7 pr-3 text-lg font-semibold focus:outline-none focus:ring-2',
                error ? 'border-red-400 focus:ring-red-200' : 'border-slate-300 focus:border-brand-500 focus:ring-brand-100')}
              {...register('budget', { valueAsNumber: true })}
            />
          </div>
          {error && <p className="mt-1.5 text-sm text-red-600">{error}</p>}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-5" aria-live="polite">
        <p className="text-sm font-semibold">Budget check</p>
        <p className="mt-1 text-sm text-slate-600">
          The cheapest in-stock version of your must-haves costs <strong className="text-slate-900">{formatMoney(cost.essentials)}</strong>
          {cost.selected > cost.essentials && <>, or <strong className="text-slate-900">{formatMoney(cost.selected)}</strong> with the nice-to-haves</>}.
        </p>
        {validBudget && (
          <>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
              <div className={clsx('h-full rounded-full', fitsEssentials ? 'bg-emerald-500' : 'bg-red-500')} style={{ width: `${usage}%` }} />
            </div>
            <p className={clsx('mt-2 text-sm font-medium', fitsEssentials ? 'text-emerald-700' : 'text-red-600')}>
              {fitsEssentials
                ? `Your budget leaves ${formatMoney(budget - cost.essentials)} to spend on quality and extras.`
                : `Raise your budget to at least ${formatMoney(cost.essentials)}, or mark something as “Already own” or “Skip”.`}
            </p>
          </>
        )}
        {cost.unavailable.length > 0 && (
          <p className="mt-2 text-sm text-amber-700">Currently out of stock: {cost.unavailable.join(', ')}.</p>
        )}
      </div>

      <fieldset>
        <legend className="text-lg font-semibold">How should we spend it?</legend>
        <div className="mt-3 grid gap-3 sm:grid-cols-3">
          {(Object.keys(qualityLabels) as Quality[]).map((q) => (
            <label key={q} className={clsx('cursor-pointer rounded-2xl border p-4', quality === q ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500' : 'border-slate-200 bg-white hover:border-slate-300')}>
              <input type="radio" value={q} className="sr-only" {...register('quality')} />
              <span className="block font-semibold">{qualityLabels[q].label}</span>
              <span className="mt-1 block text-sm text-slate-600">{qualityLabels[q].description}</span>
            </label>
          ))}
        </div>
      </fieldset>
    </section>
  );
}

function PreferencesStep({ control, register, template, byslug, errors }: StepProps & {
  template: TemplateDetail | undefined;
  byslug: Map<string, CategoryOption>;
  errors: FieldErrors<WizardValues>;
}) {
  const items = useWatch({ control, name: 'items' });
  const buying = items.filter((i) => i.kind === 'REQUIRED' || i.kind === 'OPTIONAL');
  const brands = [...new Set(buying.flatMap((i) => byslug.get(i.categorySlug)?.brands ?? []))].sort();
  const usageOptions = template?.usageOptions.length ? template.usageOptions : generalUsageOptions;
  const showSpace = buying.some((i) => SPACE_SENSITIVE_CATEGORIES.includes(i.categorySlug));

  return (
    <section className="space-y-8" aria-label="Preferences">
      <fieldset>
        <legend className="text-lg font-semibold">How will you use it?</legend>
        <p className="mt-1 text-sm text-slate-600">We favour products suited to what you pick.</p>
        <Controller
          control={control}
          name="usageTags"
          render={({ field }) => (
            <div className="mt-3 flex flex-wrap gap-2">
              {usageOptions.map((o) => (
                <ToggleChip key={o.tag} label={o.label} checked={field.value.includes(o.tag)}
                  onChange={(on) => field.onChange(on ? [...field.value, o.tag] : field.value.filter((t) => t !== o.tag))} />
              ))}
            </div>
          )}
        />
      </fieldset>

      {brands.length > 0 && (
        <fieldset>
          <legend className="text-lg font-semibold">Preferred brands <span className="text-sm font-normal text-slate-500">(optional)</span></legend>
          <Controller
            control={control}
            name="preferredBrands"
            render={({ field }) => (
              <div className="mt-3 flex flex-wrap gap-2">
                {brands.map((b) => (
                  <ToggleChip key={b} label={b} checked={field.value.includes(b)}
                    disabled={!field.value.includes(b) && field.value.length >= MAX_BRANDS}
                    onChange={(on) => field.onChange(on ? [...field.value, b] : field.value.filter((x) => x !== b))} />
                ))}
              </div>
            )}
          />
        </fieldset>
      )}

      {showSpace && (
        <fieldset>
          <legend className="text-lg font-semibold">Available space <span className="text-sm font-normal text-slate-500">(optional)</span></legend>
          <p className="mt-1 text-sm text-slate-600">We’ll only suggest furniture and equipment that fits.</p>
          <div className="mt-3 grid max-w-md grid-cols-2 gap-3">
            <TextField label="Width (cm)" inputMode="decimal" error={errors.spaceWidthCm?.message} {...register('spaceWidthCm')} />
            <TextField label="Depth (cm)" inputMode="decimal" error={errors.spaceDepthCm?.message} {...register('spaceDepthCm')} />
          </div>
        </fieldset>
      )}

      <div className="grid gap-4">
        <TextField label="Goal name" placeholder="e.g. My home office" error={errors.title?.message} {...register('title')} />
        <div>
          <label htmlFor="notes" className="mb-1.5 block text-sm font-medium text-slate-700">Anything else? <span className="font-normal text-slate-500">(optional)</span></label>
          <textarea id="notes" rows={3} maxLength={500} className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
            placeholder="e.g. Must be easy to move, prefer white finishes" {...register('notes')} />
          {errors.notes?.message && <p className="mt-1.5 text-sm text-red-600">{errors.notes.message}</p>}
        </div>
      </div>
    </section>
  );
}

function ToggleChip({ label, checked, onChange, disabled }: { label: string; checked: boolean; onChange: (on: boolean) => void; disabled?: boolean }) {
  return (
    <label className={clsx('inline-flex cursor-pointer items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium ring-1 transition-colors',
      checked ? 'bg-brand-600 text-white ring-brand-600' : 'bg-white text-slate-700 ring-slate-300 hover:bg-slate-50',
      disabled && 'cursor-not-allowed opacity-50')}>
      <input type="checkbox" className="sr-only" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      {checked && <Check className="size-3.5" aria-hidden="true" />}
      {label}
    </label>
  );
}

function ReviewStep({ values, byslug, cost }: { values: WizardValues; byslug: Map<string, CategoryOption>; cost: ReturnType<typeof estimate> }) {
  const groups: { kind: ItemKind; title: string }[] = [
    { kind: 'REQUIRED', title: 'Must-haves' },
    { kind: 'OPTIONAL', title: 'Nice to have' },
    { kind: 'OWNED', title: 'Already owned (not bought)' },
  ];
  return (
    <section aria-labelledby="review-heading" className="space-y-5">
      <h2 id="review-heading" className="text-lg font-semibold">Review your goal</h2>
      <dl className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 text-sm sm:grid-cols-3">
        <div><dt className="text-slate-500">Goal</dt><dd className="font-semibold">{values.title || 'Custom bundle'}</dd></div>
        <div><dt className="text-slate-500">Budget</dt><dd className="font-semibold">{formatMoney(values.budget)}</dd></div>
        <div><dt className="text-slate-500">Approach</dt><dd className="font-semibold">{qualityLabels[values.quality].label}</dd></div>
      </dl>
      {groups.map(({ kind, title }) => {
        const rows = values.items.filter((i) => i.kind === kind);
        if (rows.length === 0) return null;
        return (
          <div key={kind}>
            <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
            <ul className="mt-2 flex flex-wrap gap-2">
              {rows.map((i) => (
                <li key={i.categorySlug} className="rounded-full bg-slate-100 px-3 py-1 text-sm">
                  {byslug.get(i.categorySlug)?.name}{i.quantity > 1 && kind !== 'OWNED' ? ` × ${i.quantity}` : ''}
                </li>
              ))}
            </ul>
          </div>
        );
      })}
      {(values.usageTags.length > 0 || values.preferredBrands.length > 0) && (
        <p className="text-sm text-slate-600">
          {values.usageTags.length > 0 && <>Preferences: {values.usageTags.join(', ')}. </>}
          {values.preferredBrands.length > 0 && <>Brands: {values.preferredBrands.join(', ')}.</>}
        </p>
      )}
      <Alert tone={cost.essentials <= values.budget ? 'success' : 'error'}>
        Cheapest possible must-haves: {formatMoney(cost.essentials)} of your {formatMoney(values.budget)} budget.
      </Alert>
    </section>
  );
}
