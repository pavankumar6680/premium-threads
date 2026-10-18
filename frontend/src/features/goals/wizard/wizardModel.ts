import { z } from 'zod';
import type { CategoryOption, CreateGoalInput, Fit, Goal, GoalAnalysis, Quality, RequirementKind, TemplateDetail } from '../api';

export type ItemKind = RequirementKind | 'SKIP';

const spaceField = z
  .string()
  .trim()
  .regex(/^$|^\d{2,4}(\.\d)?$/, 'Enter centimetres between 10 and 2000');

export const wizardSchema = z.object({
  templateSlug: z.string().nullable(),
  title: z.string().trim().max(120, 'Use at most 120 characters'),
  rawText: z.string().max(2000).nullable(),
  items: z
    .array(
      z.object({
        categorySlug: z.string(),
        kind: z.enum(['REQUIRED', 'OPTIONAL', 'OWNED', 'SKIP']),
        quantity: z.number().int().min(1).max(10),
      }),
    )
    .refine((items) => items.some((i) => i.kind === 'REQUIRED' || i.kind === 'OPTIONAL'), {
      message: 'Choose at least one item to buy',
    }),
  budget: z
    .number({ error: 'Enter your budget' })
    .min(1, 'Enter a budget of at least $1')
    .max(1_000_000, 'Budgets above $1,000,000 are not supported'),
  quality: z.enum(['BUDGET', 'BALANCED', 'PREMIUM']),
  usageTags: z.array(z.string()),
  preferredBrands: z.array(z.string()),
  spaceWidthCm: spaceField,
  spaceDepthCm: spaceField,
  notes: z.string().max(500, 'Use at most 500 characters'),
  /** carried through from the request text or the original goal; edited in the outfit builder */
  fit: z.custom<Fit>().nullable(),
});

export type WizardValues = z.infer<typeof wizardSchema>;

/** Fields validated before leaving each step. */
export const stepFields: (keyof WizardValues)[][] = [
  ['items'],
  ['budget', 'quality'],
  ['usageTags', 'preferredBrands', 'spaceWidthCm', 'spaceDepthCm', 'notes', 'title'],
  [],
];

const base = (overrides: Partial<WizardValues>): WizardValues => ({
  templateSlug: null,
  title: '',
  rawText: null,
  items: [],
  budget: Number.NaN,
  quality: 'BALANCED' as Quality,
  usageTags: [],
  preferredBrands: [],
  spaceWidthCm: '',
  spaceDepthCm: '',
  notes: '',
  fit: null,
  ...overrides,
});

function templateItems(template: TemplateDetail) {
  return template.items.map((i) => ({
    categorySlug: i.category.slug,
    kind: (i.required ? 'REQUIRED' : i.selectedByDefault ? 'OPTIONAL' : 'SKIP') as ItemKind,
    quantity: i.quantity,
  }));
}

export function fromTemplate(template: TemplateDetail): WizardValues {
  return base({ templateSlug: template.slug, title: template.title, items: templateItems(template), budget: template.defaultBudget });
}

export function blank(): WizardValues {
  return base({});
}

/** Template rows stay visible (unmentioned optional ones as "Skip"); interpreted mentions override them. */
export function fromAnalysis(analysis: GoalAnalysis, text: string, template: TemplateDetail | undefined): WizardValues {
  const mentioned = new Map(analysis.items.map((i) => [i.categorySlug, i]));
  const rows = template
    ? template.items.map((i) => {
        const hit = mentioned.get(i.category.slug);
        return { categorySlug: i.category.slug, kind: (hit?.kind ?? 'SKIP') as ItemKind, quantity: hit?.quantity ?? i.quantity };
      })
    : [];
  const known = new Set(rows.map((r) => r.categorySlug));
  analysis.items.filter((i) => !known.has(i.categorySlug)).forEach((i) => rows.push({ ...i }));
  return base({
    templateSlug: analysis.templateSlug,
    title: analysis.title,
    rawText: text,
    items: rows,
    budget: analysis.budget ?? template?.defaultBudget ?? Number.NaN,
    quality: analysis.quality,
    usageTags: analysis.usageTags,
    preferredBrands: analysis.preferredBrands,
    fit: analysis.fit ?? null,
  });
}

/** Starts a new goal from an existing one (goals are immutable; adjusting creates a new goal). */
export function fromGoal(goal: Goal, template: TemplateDetail | undefined): WizardValues {
  const existing = new Map(goal.items.map((i) => [i.category.slug, i]));
  const rows = template
    ? template.items.map((i) => {
        const hit = existing.get(i.category.slug);
        return { categorySlug: i.category.slug, kind: (hit?.kind ?? 'SKIP') as ItemKind, quantity: hit?.quantity ?? i.quantity };
      })
    : [];
  const known = new Set(rows.map((r) => r.categorySlug));
  goal.items
    .filter((i) => !known.has(i.category.slug))
    .forEach((i) => rows.push({ categorySlug: i.category.slug, kind: i.kind, quantity: i.quantity }));
  return base({
    templateSlug: goal.template?.slug ?? null,
    title: goal.title,
    rawText: goal.rawText,
    items: rows,
    budget: goal.budget,
    quality: goal.quality,
    usageTags: goal.usageTags,
    preferredBrands: goal.preferredBrands,
    spaceWidthCm: goal.space?.widthCm != null ? String(goal.space.widthCm) : '',
    spaceDepthCm: goal.space?.depthCm != null ? String(goal.space.depthCm) : '',
    notes: goal.notes ?? '',
    fit: goal.fit ?? null,
  });
}

/** Cheapest in-stock cost: an estimate only; the server recomputes it when the goal is saved. */
export function estimate(items: WizardValues['items'], categories: Map<string, CategoryOption>) {
  const cost = (kinds: ItemKind[]) =>
    items
      .filter((i) => kinds.includes(i.kind))
      .reduce((sum, i) => sum + (categories.get(i.categorySlug)?.minPrice ?? 0) * i.quantity, 0);
  const unavailable = items
    .filter((i) => (i.kind === 'REQUIRED' || i.kind === 'OPTIONAL') && (categories.get(i.categorySlug)?.inStockCount ?? 0) === 0)
    .map((i) => categories.get(i.categorySlug)?.name ?? i.categorySlug);
  return { essentials: cost(['REQUIRED']), selected: cost(['REQUIRED', 'OPTIONAL']), unavailable };
}

export function toCreateInput(values: WizardValues): CreateGoalInput {
  const width = values.spaceWidthCm.trim();
  const depth = values.spaceDepthCm.trim();
  return {
    templateSlug: values.templateSlug,
    title: values.title.trim(),
    rawText: values.rawText,
    budget: Math.round(values.budget * 100) / 100,
    quality: values.quality,
    items: values.items
      .filter((i) => i.kind !== 'SKIP')
      .map((i) => ({ categorySlug: i.categorySlug, kind: i.kind as RequirementKind, quantity: i.quantity })),
    preferredBrands: values.preferredBrands,
    usageTags: values.usageTags,
    space: width || depth ? { widthCm: width ? Number(width) : null, depthCm: depth ? Number(depth) : null } : null,
    notes: values.notes.trim() || null,
    fit: values.fit,
  };
}
