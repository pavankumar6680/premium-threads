import { useMutation } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  Briefcase,
  Check,
  Dumbbell,
  Heart,
  PartyPopper,
  Plane,
  Sparkles,
  Sun,
  WandSparkles,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { api, ApiError } from '../../lib/api';
import { formatMoney } from '../../lib/format';
import { AiStatusPill } from '../ai/AiStatusPill';
import { useBrands, useVocabulary } from '../catalog/api';
import {
  qualityLabels,
  useAnalyzeGoal,
  useGoalTemplate,
  useGoalTemplates,
  type CreateGoalInput,
  type Gender,
  type Goal,
  type GoalAnalysis,
  type Quality,
  type TemplateDetail,
} from '../goals/api';
import type { RecommendationRun } from '../recommendations/api';

type PieceKind = 'REQUIRED' | 'OPTIONAL' | 'SKIP';

type Choices = {
  gender: Gender | null;
  templateSlug: string | null;
  /** piece choices that differ from the occasion's defaults */
  pieces: Record<string, PieceKind>;
  budget: string;
  quality: Quality;
  sizeAlpha: string | null;
  sizeWaist: string | null;
  colors: string[];
  styles: string[];
  brands: string[];
  text: string;
};

const INITIAL: Choices = {
  gender: null,
  templateSlug: null,
  pieces: {},
  budget: '',
  quality: 'BALANCED',
  sizeAlpha: null,
  sizeWaist: null,
  colors: [],
  styles: [],
  brands: [],
  text: '',
};

const MAX_COLORS = 6;
const BUDGET_PRESETS = [100, 200, 350, 500];

const OCCASION_ICONS: [string, LucideIcon][] = [
  ['weekend', Sun],
  ['office', Briefcase],
  ['party', PartyPopper],
  ['date', Heart],
  ['festive', Sparkles],
  ['street', Zap],
  ['travel', Plane],
  ['workout', Dumbbell],
];

const EXAMPLE = 'A stylish casual outfit for a weekend party under $200 for him, size M, waist 32, in navy or olive.';

function occasionIcon(slug: string): LucideIcon {
  return OCCASION_ICONS.find(([key]) => slug.startsWith(key))?.[1] ?? Sparkles;
}

/** "Weekend casual (men)" → "Weekend casual" */
function occasionName(title: string) {
  return title.replace(/\s*\((men|women)\)\s*$/i, '');
}

function defaultKind(item: TemplateDetail['items'][number]): PieceKind {
  return item.required ? 'REQUIRED' : item.selectedByDefault ? 'OPTIONAL' : 'SKIP';
}

/**
 * Build Your Outfit: occasion, wardrobe, budget, sizes, colors, styles and brands, or a sentence that pre-fills
 * them. Saving creates a goal (with the wearer's fit) and runs the recommendation engine, which picks pieces
 * from different brands that work together and chooses a size and color for each.
 */
export function OutfitBuilderPage() {
  const navigate = useNavigate();
  const [choices, setChoices] = useState<Choices>(INITIAL);
  const [error, setError] = useState<string | null>(null);
  const templates = useGoalTemplates();
  const template = useGoalTemplate(choices.templateSlug);
  const vocabulary = useVocabulary();
  const brands = useBrands();
  const analyze = useAnalyzeGoal();

  const set = <K extends keyof Choices>(key: K, value: Choices[K]) => setChoices((c) => ({ ...c, [key]: value }));
  const toggle = (key: 'colors' | 'styles' | 'brands', value: string, max = Infinity) =>
    setChoices((c) => {
      const list = c[key];
      if (list.includes(value)) return { ...c, [key]: list.filter((v) => v !== value) };
      return list.length >= max ? c : { ...c, [key]: [...list, value] };
    });

  const occasions = (templates.data ?? []).filter((t) => choices.gender && t.slug.endsWith(`-${choices.gender.toLowerCase()}`));
  const detail = template.data?.slug === choices.templateSlug ? template.data : undefined;
  const kindOf = (item: TemplateDetail['items'][number]) => choices.pieces[item.category.slug] ?? defaultKind(item);
  const buying = detail?.items.filter((i) => kindOf(i) !== 'SKIP') ?? [];
  const budget = Number(choices.budget);
  const essentials = detail?.items
    .filter((i) => kindOf(i) === 'REQUIRED')
    .reduce((sum, i) => sum + (i.category.minPrice ?? 0) * i.quantity, 0) ?? 0;
  const wardrobeBrands = (brands.data ?? []).filter(
    (b) => !choices.gender || b.categories.includes(choices.gender.toLowerCase()) || b.categories.includes('accessories'),
  );

  const chooseGender = (gender: Gender) =>
    setChoices((c) => {
      if (c.gender === gender) return c;
      // keep the same occasion for the other wardrobe when there is one
      const base = c.templateSlug?.replace(/-(men|women)$/, '');
      const twin = base && templates.data?.find((t) => t.slug === `${base}-${gender.toLowerCase()}`);
      return { ...c, gender, templateSlug: twin ? twin.slug : null, pieces: {} };
    });

  const chooseOccasion = (slug: string) => {
    const summary = templates.data?.find((t) => t.slug === slug);
    setChoices((c) => ({
      ...c,
      templateSlug: slug,
      pieces: {},
      budget: c.budget || (summary ? String(summary.defaultBudget) : ''),
    }));
  };

  const applyAnalysis = (analysis: GoalAnalysis) => {
    const fit = analysis.fit;
    const gender = fit?.gender ?? (analysis.templateSlug?.endsWith('-women') ? 'WOMEN' : analysis.templateSlug?.endsWith('-men') ? 'MEN' : null);
    const pieces: Record<string, PieceKind> = {};
    analysis.items.forEach((i) => {
      if (i.kind !== 'OWNED') pieces[i.categorySlug] = i.kind;
    });
    setChoices((c) => ({
      ...c,
      gender: gender ?? c.gender,
      templateSlug: analysis.templateSlug ?? c.templateSlug,
      pieces: analysis.templateSlug ? pieces : c.pieces,
      budget: analysis.budget != null ? String(analysis.budget) : c.budget,
      quality: analysis.quality,
      sizeAlpha: fit?.sizeAlpha ?? c.sizeAlpha,
      sizeWaist: fit?.sizeWaist ?? c.sizeWaist,
      colors: fit?.colors.length ? fit.colors.slice(0, MAX_COLORS) : c.colors,
      styles: analysis.usageTags.length ? analysis.usageTags : c.styles,
      brands: analysis.preferredBrands.length ? analysis.preferredBrands : c.brands,
    }));
    setError(analysis.templateSlug ? null : 'We could not tell the occasion from that. Pick one below.');
  };

  const create = useMutation({
    mutationFn: async (input: CreateGoalInput) => {
      const goal = await api.post<Goal>('/goals', input);
      return api.post<RecommendationRun>(`/goals/${goal.id}/recommendations`);
    },
    onSuccess: (run) => navigate(`/recommendations/${run.id}`),
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not build your outfit. Please try again.'),
  });

  const missing = !choices.gender
    ? 'Choose who the outfit is for.'
    : !detail
      ? 'Choose an occasion.'
      : buying.length === 0
        ? 'Choose at least one piece.'
        : !(budget >= 1)
          ? 'Enter your budget.'
          : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (missing || !detail) {
      setError(missing);
      return;
    }
    setError(null);
    create.mutate({
      templateSlug: detail.slug,
      title: detail.title,
      rawText: choices.text.trim() || null,
      budget: Math.round(budget * 100) / 100,
      quality: choices.quality,
      items: buying.map((i) => ({ categorySlug: i.category.slug, kind: kindOf(i) as 'REQUIRED' | 'OPTIONAL', quantity: i.quantity })),
      preferredBrands: choices.brands,
      usageTags: choices.styles.filter((s) => detail.usageOptions.some((o) => o.tag === s)),
      space: null,
      notes: null,
      fit: { gender: choices.gender, sizeAlpha: choices.sizeAlpha, sizeWaist: choices.sizeWaist, colors: choices.colors },
    });
  };

  const alphaSizes = vocabulary.data?.sizes.ALPHA ?? [];
  const waistSizes = vocabulary.data?.sizes.WAIST ?? [];

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <header className="max-w-3xl">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-violet-700">Build Your Outfit</p>
        <h1 className="mt-2 font-display text-4xl font-semibold leading-tight sm:text-5xl">One occasion. One budget. One complete look.</h1>
        <p className="mt-3 text-slate-600">
          Tell us the occasion and your budget. We mix pieces from different brands that work together, in your size and colors.
        </p>
      </header>

      <form
        className="mt-8 rounded-3xl bg-violet-100 p-4 sm:p-5"
        onSubmit={(e) => {
          e.preventDefault();
          const text = choices.text.trim();
          if (text) analyze.mutate(text, { onSuccess: applyAnalysis });
        }}
        aria-label="Describe your outfit"
      >
        <label htmlFor="outfit-text" className="text-sm font-semibold text-violet-900">Describe it in your own words (optional)</label>
        <textarea
          id="outfit-text"
          rows={2}
          maxLength={2000}
          value={choices.text}
          onChange={(e) => set('text', e.target.value)}
          placeholder={EXAMPLE}
          className="mt-2 block w-full resize-y rounded-2xl border-0 bg-white p-3 text-base shadow-card focus:outline-none focus:ring-2 focus:ring-violet-300"
        />
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" className="text-sm font-semibold text-violet-700 hover:underline" onClick={() => set('text', EXAMPLE)}>
              Try an example
            </button>
            <AiStatusPill />
          </div>
          <Button type="submit" variant="secondary" isLoading={analyze.isPending} disabled={!choices.text.trim()} className="rounded-full">
            <Sparkles className="size-4" aria-hidden="true" /> Fill in the choices
          </Button>
        </div>
        {analyze.isError && <div className="mt-3"><Alert tone="error">Could not read that. Please make your choices below.</Alert></div>}
      </form>

      <form onSubmit={submit} className="mt-8 grid gap-8 lg:grid-cols-[1fr_320px]" aria-label="Outfit choices">
        <div className="space-y-6">
          <Section title="Who is it for?" step={1}>
            <div className="grid grid-cols-2 gap-3 sm:max-w-sm" role="group" aria-label="Wardrobe">
              {(['MEN', 'WOMEN'] as Gender[]).map((g) => (
                <Chip key={g} large pressed={choices.gender === g} onClick={() => chooseGender(g)}>
                  {g === 'MEN' ? 'Men' : 'Women'}
                </Chip>
              ))}
            </div>
          </Section>

          <Section title="Occasion" step={2} muted={!choices.gender}>
            {!choices.gender ? (
              <p className="text-sm text-slate-500">Choose men or women first.</p>
            ) : (
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4" role="group" aria-label="Occasion">
                {occasions.map((t) => {
                  const Icon = occasionIcon(t.slug);
                  const active = choices.templateSlug === t.slug;
                  return (
                    <button
                      key={t.slug}
                      type="button"
                      aria-pressed={active}
                      onClick={() => chooseOccasion(t.slug)}
                      className={clsx(
                        'flex flex-col items-start rounded-2xl border bg-white p-4 text-left transition-shadow hover:shadow-card',
                        active ? 'border-violet-600 ring-2 ring-violet-600' : 'border-slate-200',
                      )}
                    >
                      <Icon className={clsx('size-5', active ? 'text-violet-700' : 'text-slate-500')} aria-hidden="true" />
                      <span className="mt-2 font-semibold">{occasionName(t.title)}</span>
                      <span className="mt-0.5 text-xs text-slate-500">from {formatMoney(t.essentialsMinimum)}</span>
                    </button>
                  );
                })}
              </div>
            )}
          </Section>

          {detail && (
            <Section title="Pieces" step={3}>
              <ul className="divide-y divide-slate-100">
                {detail.items.map((item) => {
                  const kind = kindOf(item);
                  return (
                    <li key={item.category.slug} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                      <div>
                        <p className="font-medium">{item.category.name}</p>
                        <p className="text-xs text-slate-500">
                          {item.category.inStockCount > 0 && item.category.minPrice != null
                            ? `${item.category.inStockCount} in stock · from ${formatMoney(item.category.minPrice)}`
                            : 'Currently out of stock'}
                        </p>
                      </div>
                      <div className="flex gap-1.5" role="group" aria-label={`${item.category.name}`}>
                        {(['REQUIRED', 'OPTIONAL', 'SKIP'] as PieceKind[]).map((k) => (
                          <Chip
                            key={k}
                            small
                            pressed={kind === k}
                            onClick={() => setChoices((c) => ({ ...c, pieces: { ...c.pieces, [item.category.slug]: k } }))}
                          >
                            {k === 'REQUIRED' ? 'Need it' : k === 'OPTIONAL' ? 'Nice to have' : 'Skip'}
                          </Chip>
                        ))}
                      </div>
                    </li>
                  );
                })}
              </ul>
            </Section>
          )}

          <Section title="Your sizes" step={detail ? 4 : 3} hint="Optional. We only suggest pieces in stock in your size.">
            {alphaSizes.length > 0 && (
              <SizeRow label="Tops, dresses & outerwear" sizes={alphaSizes} value={choices.sizeAlpha} onChange={(s) => set('sizeAlpha', s)} />
            )}
            {waistSizes.length > 0 && (
              <div className="mt-4">
                <SizeRow label="Waist (jeans, trousers & skirts)" sizes={waistSizes} value={choices.sizeWaist} onChange={(s) => set('sizeWaist', s)} />
              </div>
            )}
          </Section>

          <Section title="Colors you love" step={detail ? 5 : 4} hint={`Optional, up to ${MAX_COLORS}. Neutrals fill in the rest, with at most one accent color.`}>
            <div className="flex flex-wrap gap-2.5" role="group" aria-label="Colors">
              {vocabulary.data?.colors.map((c) => {
                const active = choices.colors.includes(c.name);
                return (
                  <button
                    key={c.name}
                    type="button"
                    aria-pressed={active}
                    aria-label={c.name}
                    title={c.name}
                    onClick={() => toggle('colors', c.name, MAX_COLORS)}
                    className={clsx(
                      'relative grid size-9 place-items-center rounded-full ring-1 ring-black/15 ring-offset-2 transition-shadow',
                      active ? 'ring-2 ring-slate-900' : 'hover:ring-slate-400',
                    )}
                    style={{ backgroundColor: c.hex ?? '#ddd' }}
                  >
                    {active && <Check className="size-4 text-white mix-blend-difference" aria-hidden="true" />}
                  </button>
                );
              })}
            </div>
            {choices.colors.length > 0 && <p className="mt-2 text-sm text-slate-600">{choices.colors.join(', ')}</p>}
          </Section>

          {detail && detail.usageOptions.length > 0 && (
            <Section title="Style" step={6} hint="Optional.">
              <div className="flex flex-wrap gap-2" role="group" aria-label="Style">
                {detail.usageOptions.map((o) => (
                  <Chip key={o.tag} pressed={choices.styles.includes(o.tag)} onClick={() => toggle('styles', o.tag)}>{o.label}</Chip>
                ))}
              </div>
            </Section>
          )}

          <Section title="Favorite brands" step={detail ? 7 : 5} hint="Optional. Preferred brands are favored, others still fill gaps.">
            <div className="flex flex-wrap gap-2" role="group" aria-label="Brands">
              {wardrobeBrands.map((b) => (
                <Chip key={b.slug} pressed={choices.brands.includes(b.name)} onClick={() => toggle('brands', b.name)}>{b.name}</Chip>
              ))}
            </div>
          </Section>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start" aria-label="Budget and summary">
          <section className="rounded-3xl bg-white p-5 shadow-card">
            <label htmlFor="outfit-budget" className="font-semibold">Budget</label>
            <div className="relative mt-2">
              <span className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-slate-500">$</span>
              <input
                id="outfit-budget"
                type="number"
                min={1}
                step="1"
                inputMode="decimal"
                value={choices.budget}
                onChange={(e) => set('budget', e.target.value)}
                className="block h-11 w-full rounded-xl border border-slate-300 pl-7 pr-3 text-lg font-semibold focus:border-violet-500 focus:outline-none focus:ring-2 focus:ring-violet-100"
              />
            </div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {BUDGET_PRESETS.map((b) => (
                <Chip key={b} small pressed={budget === b} onClick={() => set('budget', String(b))}>{formatMoney(b)}</Chip>
              ))}
            </div>
            {detail && essentials > 0 && (
              <p className={clsx('mt-3 text-xs', budget >= essentials ? 'text-slate-600' : 'font-semibold text-red-600')}>
                The pieces you need start at {formatMoney(essentials)} in total.
              </p>
            )}
            <fieldset className="mt-4">
              <legend className="text-sm font-semibold">Priority</legend>
              <div className="mt-2 grid grid-cols-3 gap-1.5">
                {(Object.keys(qualityLabels) as Quality[]).map((q) => (
                  <Chip key={q} small pressed={choices.quality === q} onClick={() => set('quality', q)}>{qualityLabels[q].label}</Chip>
                ))}
              </div>
              <p className="mt-1.5 text-xs text-slate-500">{qualityLabels[choices.quality].description}</p>
            </fieldset>
          </section>

          <section className="rounded-3xl bg-white p-5 text-sm shadow-card" aria-label="Your choices">
            <dl className="space-y-1.5">
              <Summary term="For" value={choices.gender ? (choices.gender === 'MEN' ? 'Men' : 'Women') : '—'} />
              <Summary term="Occasion" value={detail ? occasionName(detail.title) : '—'} />
              <Summary term="Pieces" value={buying.length ? String(buying.length) : '—'} />
              <Summary term="Sizes" value={[choices.sizeAlpha, choices.sizeWaist && `W${choices.sizeWaist}`].filter(Boolean).join(' · ') || 'Choose later'} />
            </dl>
            <Button type="submit" size="lg" className="mt-4 w-full rounded-full" isLoading={create.isPending}>
              <WandSparkles className="size-5" aria-hidden="true" /> Style my outfit
            </Button>
            {error && <p className="mt-2 text-sm font-medium text-red-600" role="alert">{error}</p>}
            <p className="mt-3 text-xs text-slate-500">Demo store: products and brands are mock data for illustration.</p>
          </section>
        </aside>
      </form>
    </div>
  );
}

function Section({ title, step, hint, muted, children }: { title: string; step: number; hint?: string; muted?: boolean; children: ReactNode }) {
  return (
    <section className={clsx('rounded-3xl bg-white p-5 shadow-card', muted && 'opacity-70')} aria-label={title}>
      <h2 className="flex items-center gap-2.5 text-lg font-bold">
        <span className="grid size-7 place-items-center rounded-full bg-violet-100 text-sm font-bold text-violet-700" aria-hidden="true">{step}</span>
        {title}
      </h2>
      {hint && <p className="mt-1 text-sm text-slate-500">{hint}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

function Chip({ pressed, onClick, children, small, large }: {
  pressed: boolean;
  onClick: () => void;
  children: ReactNode;
  small?: boolean;
  large?: boolean;
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={clsx(
        'inline-flex items-center justify-center rounded-full border font-semibold transition-colors',
        small ? 'h-8 px-3 text-xs' : large ? 'h-12 px-5 text-base' : 'h-9 px-4 text-sm',
        pressed ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white text-slate-800 hover:border-slate-900',
      )}
    >
      {children}
    </button>
  );
}

function SizeRow({ label, sizes, value, onChange }: { label: string; sizes: string[]; value: string | null; onChange: (s: string | null) => void }) {
  return (
    <div role="group" aria-label={label}>
      <p className="text-sm font-medium text-slate-700">{label}</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {sizes.map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={value === s}
            onClick={() => onChange(value === s ? null : s)}
            className={clsx(
              'h-10 min-w-12 rounded-xl border px-3 text-sm font-semibold transition-colors',
              value === s ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white hover:border-slate-900',
            )}
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}

function Summary({ term, value }: { term: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-slate-500">{term}</dt>
      <dd className="text-right font-semibold">{value}</dd>
    </div>
  );
}
