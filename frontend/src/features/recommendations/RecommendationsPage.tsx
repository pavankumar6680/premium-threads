import { CircleAlert, RefreshCw, SlidersHorizontal } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button, buttonClasses } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { ApiError } from '../../lib/api';
import { AiOverview } from '../ai/AiOverview';
import { formatMoney } from '../../lib/format';
import { BundleCard } from './BundleCard';
import { useGenerateRecommendations, useRecommendationRun, type RecommendationRun } from './api';

export function RecommendationsPage() {
  const { runId = '' } = useParams();
  const { data: run, isPending, error } = useRecommendationRun(runId);

  if (isPending) return <PageSpinner />;
  if (error) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400);
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="text-2xl font-bold">{notFound ? 'Recommendations not found' : 'Something went wrong'}</h1>
        <Link to="/goals" className={buttonClasses('primary', 'md', 'mt-6')}>Start a goal</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <Header run={run} />
      <AiOverview runId={run.id} />
      {run.status === 'INFEASIBLE' ? <Infeasible run={run} /> : <Bundles run={run} />}
      {run.bundles.length > 0 && (
      <p className="mt-10 text-center text-xs text-slate-400">
        Optimizer {run.stats.algorithmVersion} evaluated {run.stats.nodesExplored.toLocaleString('en-US')} combinations in{' '}
        {run.stats.durationMs} ms using live prices, stock and compatibility rules.
      </p>
      )}
    </div>
  );
}

function Header({ run }: { run: RecommendationRun }) {
  const navigate = useNavigate();
  const regenerate = useGenerateRecommendations(run.goalId);
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <Link to={`/goals/${run.goalId}`} className="text-sm font-semibold text-brand-700 hover:underline">← {run.goalTitle}</Link>
        <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Your bundles</h1>
        <p className="mt-1 text-sm text-slate-600">Budget {formatMoney(run.budget)}</p>
      </div>
      <div className="flex gap-2">
        <Link to={`/goals/new?from=${run.goalId}`} className={buttonClasses('secondary', 'sm')}>
          <SlidersHorizontal className="size-4" aria-hidden="true" /> Adjust goal
        </Link>
        <Button
          variant="secondary"
          size="sm"
          isLoading={regenerate.isPending}
          onClick={() => regenerate.mutate(undefined, { onSuccess: (next) => navigate(`/recommendations/${next.id}`) })}
        >
          <RefreshCw className="size-4" aria-hidden="true" /> Refresh with latest prices
        </Button>
      </div>
    </div>
  );
}

function Bundles({ run }: { run: RecommendationRun }) {
  return (
    <>
      {run.notes.length > 0 && (
        <div className="mt-6 space-y-2">
          {run.notes.map((note) => <Alert key={note} tone="info">{note}</Alert>)}
        </div>
      )}
      <div className={`mt-6 grid gap-6 ${run.bundles.length >= 3 ? 'lg:grid-cols-3' : run.bundles.length === 2 ? 'md:grid-cols-2' : 'max-w-xl'}`}>
        {run.bundles.map((bundle) => (
          <BundleCard key={bundle.id} bundle={bundle} recommended={bundle.tier === run.preferredTier} />
        ))}
      </div>
    </>
  );
}

function Infeasible({ run }: { run: RecommendationRun }) {
  return (
    <section className="mt-8 max-w-3xl rounded-2xl border border-red-200 bg-white p-6 shadow-sm" aria-labelledby="infeasible-heading">
      <h2 id="infeasible-heading" className="flex items-center gap-2 text-lg font-bold text-red-700">
        <CircleAlert className="size-5" aria-hidden="true" /> We couldn’t build a bundle for this goal
      </h2>
      <ul className="mt-3 list-disc space-y-1 pl-5 text-slate-700">
        {run.reasons.map((reason) => <li key={reason}>{reason}</li>)}
      </ul>
      {run.minimumFeasibleCost !== null && (
        <p className="mt-4 rounded-lg bg-slate-50 p-3 text-sm">
          Cheapest compatible setup: <strong>{formatMoney(run.minimumFeasibleCost)}</strong> · your budget: <strong>{formatMoney(run.budget)}</strong>
        </p>
      )}
      {run.suggestions.length > 0 && (
        <>
          <h3 className="mt-5 font-semibold">How to make it work</h3>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
            {run.suggestions.map((s) => <li key={s}>{s}</li>)}
          </ul>
        </>
      )}
      <Link to={`/goals/new?from=${run.goalId}`} className={buttonClasses('primary', 'md', 'mt-6')}>Adjust my goal</Link>
    </section>
  );
}
