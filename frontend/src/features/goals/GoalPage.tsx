import clsx from 'clsx';
import { CircleCheck, CircleAlert, Link2, SlidersHorizontal, Sparkles } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router';
import { Button, buttonClasses } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { ApiError } from '../../lib/api';
import { formatDate, formatMoney } from '../../lib/format';
import { categoryVisual } from '../catalog/categoryVisuals';
import { useGenerateRecommendations, useLatestRun } from '../recommendations/api';
import { kindLabels, qualityLabels, useGoal, type Goal, type RequirementKind } from './api';

export function GoalPage() {
  const { id = '' } = useParams();
  const { data: goal, isPending, error } = useGoal(id);
  const toast = useToast();

  if (isPending) return <PageSpinner />;
  if (error) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400);
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="text-2xl font-bold">{notFound ? 'Goal not found' : 'Something went wrong'}</h1>
        <Link to="/goals" className={buttonClasses('primary', 'md', 'mt-6')}>Start a new goal</Link>
      </div>
    );
  }

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      toast.show('Link copied');
    } catch {
      toast.show('Could not copy the link', 'error');
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          {goal.template?.title !== goal.title && (
            <p className="text-sm font-semibold text-brand-700">{goal.template ? goal.template.title : 'Custom goal'}</p>
          )}
          <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">{goal.title}</h1>
          <p className="mt-1 text-sm text-slate-600">
            Budget {formatMoney(goal.budget)} · {qualityLabels[goal.quality].label} · created {formatDate(goal.createdAt)}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={copyLink}><Link2 className="size-4" aria-hidden="true" /> Copy link</Button>
          <Link to={`/goals/new?from=${goal.id}`} className={buttonClasses('secondary', 'sm')}>
            <SlidersHorizontal className="size-4" aria-hidden="true" /> Adjust goal
          </Link>
        </div>
      </div>

      {goal.rawText && <blockquote className="mt-4 border-l-4 border-brand-200 pl-4 text-sm italic text-slate-600">“{goal.rawText}”</blockquote>}

      <EstimateCard goal={goal} />
      <RecommendationsCta goal={goal} />

      <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_280px]">
        <div className="space-y-6">
          {(['REQUIRED', 'OPTIONAL', 'OWNED'] as RequirementKind[]).map((kind) => (
            <ItemGroup key={kind} goal={goal} kind={kind} />
          ))}
        </div>
        <aside className="space-y-4 text-sm">
          {goal.usageTags.length > 0 && <Facts title="Usage" values={goal.usageTags} />}
          {goal.preferredBrands.length > 0 && <Facts title="Preferred brands" values={goal.preferredBrands} />}
          {goal.space && (
            <Facts title="Available space" values={[`${goal.space.widthCm ?? '–'} × ${goal.space.depthCm ?? '–'} cm (W × D)`]} />
          )}
          {goal.notes && <Facts title="Notes" values={[goal.notes]} />}
        </aside>
      </div>
    </div>
  );
}

function EstimateCard({ goal }: { goal: Goal }) {
  const { estimate, budget } = goal;
  const Icon = estimate.essentialsFit ? CircleCheck : CircleAlert;
  return (
    <section className={clsx('mt-6 rounded-2xl border p-5', estimate.essentialsFit ? 'border-emerald-200 bg-emerald-50' : 'border-red-200 bg-red-50')} aria-label="Budget check">
      <div className="flex gap-3">
        <Icon className={clsx('mt-0.5 size-6 shrink-0', estimate.essentialsFit ? 'text-emerald-600' : 'text-red-600')} aria-hidden="true" />
        <div className="text-sm">
          <p className="font-semibold text-slate-900">
            {estimate.essentialsFit ? 'Your budget covers the essentials' : 'Your budget is below the cheapest possible setup'}
          </p>
          <p className="mt-1 text-slate-700">
            Cheapest in-stock must-haves: <strong>{formatMoney(estimate.essentialsMinimum)}</strong>
            {estimate.selectedMinimum > estimate.essentialsMinimum && <> · with nice-to-haves: <strong>{formatMoney(estimate.selectedMinimum)}</strong></>}
            {' '}· budget: <strong>{formatMoney(budget)}</strong>
          </p>
          {estimate.unavailableCategories.length > 0 && (
            <p className="mt-1 text-amber-800">Currently out of stock: {estimate.unavailableCategories.join(', ')}.</p>
          )}
        </div>
      </div>
    </section>
  );
}

function RecommendationsCta({ goal }: { goal: Goal }) {
  const navigate = useNavigate();
  const latest = useLatestRun(goal.id);
  const generate = useGenerateRecommendations(goal.id);
  const run = () => generate.mutate(undefined, { onSuccess: (r) => navigate(`/recommendations/${r.id}`) });

  return (
    <section className="mt-4 flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-brand-200 bg-brand-50 p-5" aria-label="Recommendations">
      <div>
        <p className="flex items-center gap-2 font-semibold text-brand-900">
          <Sparkles className="size-5" aria-hidden="true" /> Budget, Balanced and Premium bundles
        </p>
        <p className="mt-1 text-sm text-brand-900/80">
          We search every compatible combination of in-stock products to get the most out of your budget.
        </p>
        {generate.isError && (
          <p className="mt-1 text-sm text-red-700">{generate.error instanceof ApiError ? generate.error.message : 'Could not generate bundles.'}</p>
        )}
      </div>
      <div className="flex gap-2">
        {latest.data && (
          <Link to={`/recommendations/${latest.data.id}`} className={buttonClasses('secondary', 'md')}>View latest bundles</Link>
        )}
        <Button onClick={run} isLoading={generate.isPending}>{latest.data ? 'Generate again' : 'Get my bundles'}</Button>
      </div>
    </section>
  );
}

const groupTitles: Record<RequirementKind, string> = { REQUIRED: 'Must-haves', OPTIONAL: 'Nice to have', OWNED: 'Already owned' };

function ItemGroup({ goal, kind }: { goal: Goal; kind: RequirementKind }) {
  const items = goal.items.filter((i) => i.kind === kind);
  if (items.length === 0) return null;
  return (
    <section aria-labelledby={`group-${kind}`}>
      <h2 id={`group-${kind}`} className="text-lg font-semibold">{groupTitles[kind]}</h2>
      <ul className="mt-3 divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
        {items.map(({ category, quantity }) => {
          const { icon: Icon, tile } = categoryVisual(category.slug);
          const shopLink = new URLSearchParams({ category: category.slug });
          goal.preferredBrands.filter((b) => category.brands.includes(b)).forEach((b) => shopLink.append('brand', b));
          return (
            <li key={category.slug} className="flex flex-wrap items-center gap-4 p-4">
              <span className={clsx('flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br', tile)}>
                <Icon className="size-5" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold">
                  {category.name}
                  {quantity > 1 && kind !== 'OWNED' && <span className="text-slate-500"> × {quantity}</span>}
                </p>
                <p className="text-xs text-slate-500">
                  {kind === 'OWNED'
                    ? kindLabels.OWNED
                    : category.minPrice !== null
                      ? `${formatMoney(category.minPrice)} – ${formatMoney(category.maxPrice ?? category.minPrice)} · ${category.inStockCount} in stock`
                      : 'Out of stock'}
                </p>
              </div>
              {kind !== 'OWNED' && (
                <Link to={`/shop?${shopLink.toString()}`} className={buttonClasses('ghost', 'sm')}>Browse</Link>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function Facts({ title, values }: { title: string; values: string[] }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-1 text-slate-600">{values.join(', ')}</p>
    </div>
  );
}

