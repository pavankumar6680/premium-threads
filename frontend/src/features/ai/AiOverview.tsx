import { Sparkles } from 'lucide-react';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { useAiStatus, useExplainRun } from './api';

/**
 * "Explain with AI": a plain-language overview written by the local model from the run's facts only.
 * Hidden when AI assistance is switched off; the deterministic explanations remain on every item.
 */
export function AiOverview({ runId }: { runId: string }) {
  const status = useAiStatus();
  const explain = useExplainRun(runId);
  if (!status.data?.enabled) return null;

  const result = explain.data;
  return (
    <section className="mt-6 rounded-2xl border border-violet-200 bg-violet-50 p-5" aria-labelledby="ai-overview-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="ai-overview-heading" className="flex items-center gap-2 font-semibold text-violet-900">
          <Sparkles className="size-5" aria-hidden="true" /> AI overview
        </h2>
        {result?.status !== 'OK' && (
          <Button variant="secondary" size="sm" onClick={() => explain.mutate()} isLoading={explain.isPending}
            disabled={!status.data.available}>
            Explain with AI
          </Button>
        )}
      </div>
      {!status.data.available && <p className="mt-2 text-sm text-violet-900/80">{status.data.message}</p>}
      {explain.isPending && <p className="mt-2 text-sm text-violet-900/80">The local model is writing an overview. This can take a little while…</p>}
      {result?.status === 'OK' && (
        <>
          <p className="mt-3 leading-relaxed text-slate-800">{result.text}</p>
          <p className="mt-2 text-xs text-violet-900/70">
            Written by {result.model} from these results only, and checked so it never adds prices or numbers that aren’t shown above.
          </p>
        </>
      )}
      {result && result.status !== 'OK' && (
        <div className="mt-3"><Alert tone="info">{result.message}</Alert></div>
      )}
      {explain.isError && <div className="mt-3"><Alert tone="error">Could not reach the AI service.</Alert></div>}
    </section>
  );
}
