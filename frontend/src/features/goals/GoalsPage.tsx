import { PencilRuler, Sparkles } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { ApiError } from '../../lib/api';
import { AiStatusPill } from '../ai/AiStatusPill';
import { useAnalyzeGoal, useGoalTemplates } from './api';
import { TemplateCard } from './TemplateCard';

const EXAMPLE =
  'I want to set up a professional work-from-home office with a budget of $1,200. I need a desk, ergonomic chair, monitor, keyboard, mouse, and lighting.';

export function GoalsPage() {
  const navigate = useNavigate();
  const templates = useGoalTemplates();
  const analyze = useAnalyzeGoal();
  const [text, setText] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const goal = text.trim();
    if (!goal) return;
    analyze.mutate(goal, { onSuccess: (analysis) => navigate('/goals/new', { state: { analysis, text: goal } }) });
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-10">
      <div className="max-w-3xl">
        <p className="text-sm font-semibold text-brand-700">Build a bundle</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight sm:text-4xl">What do you want to accomplish?</h1>
        <p className="mt-2 text-slate-600">Describe it in your own words, or start from one of the goals below.</p>
      </div>

      <form onSubmit={submit} className="mt-6 max-w-3xl rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <label htmlFor="goal-text" className="sr-only">Describe your goal</label>
        <textarea
          id="goal-text"
          rows={4}
          maxLength={2000}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={EXAMPLE}
          className="block w-full resize-y rounded-lg border-0 p-1 text-base focus:outline-none focus:ring-0"
        />
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3">
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" className="text-sm font-medium text-brand-700 hover:underline" onClick={() => setText(EXAMPLE)}>
              Try an example
            </button>
            <AiStatusPill />
          </div>
          <Button type="submit" isLoading={analyze.isPending} disabled={!text.trim()}>
            <Sparkles className="size-4" aria-hidden="true" /> Plan my bundle
          </Button>
        </div>
        {analyze.isError && (
          <div className="mt-3">
            <Alert tone="error">{analyze.error instanceof ApiError ? analyze.error.message : 'Could not read your goal. Please try again.'}</Alert>
          </div>
        )}
      </form>

      <section className="mt-12" aria-labelledby="popular-goals">
        <h2 id="popular-goals" className="text-xl font-bold">Popular goals</h2>
        {templates.isError && <div className="mt-4"><Alert tone="error">{templates.error.message}</Alert></div>}
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {templates.data?.map((t) => (
            <TemplateCard key={t.slug} template={t} />
          ))}
          <Link
            to="/goals/new"
            className="flex flex-col items-start justify-center rounded-2xl border border-dashed border-slate-300 bg-white p-5 hover:border-brand-400"
          >
            <PencilRuler className="size-6 text-slate-500" aria-hidden="true" />
            <h3 className="mt-3 text-lg font-semibold">Start from scratch</h3>
            <p className="mt-1 text-sm text-slate-600">Pick any categories and build a custom goal.</p>
          </Link>
        </div>
      </section>
    </div>
  );
}
