import { Target } from 'lucide-react';
import { Link } from 'react-router';
import { formatDate, formatMoney, pluralize } from '../../lib/format';
import { qualityLabels, useMyGoals } from './api';

export function MyGoalsSection() {
  const { data } = useMyGoals();

  return (
    <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" aria-labelledby="my-goals-heading">
      <div className="flex items-center justify-between gap-3">
        <h2 id="my-goals-heading" className="flex items-center gap-2 text-lg font-semibold">
          <Target className="size-5 text-brand-600" aria-hidden="true" /> My goals
        </h2>
        <Link to="/goals" className="text-sm font-semibold text-brand-700 hover:underline">New goal</Link>
      </div>
      {data && data.content.length === 0 && (
        <p className="mt-4 text-sm text-slate-600">You have not created any goals yet.</p>
      )}
      <ul className="mt-4 divide-y divide-slate-100">
        {data?.content.map((goal) => (
          <li key={goal.id}>
            <Link to={`/goals/${goal.id}`} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm hover:text-brand-700">
              <span className="font-semibold">{goal.title}</span>
              <span className="text-slate-500">
                {formatMoney(goal.budget)} · {qualityLabels[goal.quality].label} · {pluralize(goal.itemCount, 'item')} · {formatDate(goal.createdAt)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
