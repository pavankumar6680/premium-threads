import clsx from 'clsx';
import { Sparkles } from 'lucide-react';
import { useAiStatus } from './api';

/** Shows whether goals are read with the local AI model or with the built-in rules only. */
export function AiStatusPill() {
  const { data } = useAiStatus();
  if (!data) return null;
  return (
    <span
      title={data.message}
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium',
        data.available ? 'bg-violet-100 text-violet-800' : 'bg-slate-100 text-slate-600',
      )}
    >
      <Sparkles className="size-3.5" aria-hidden="true" />
      {data.available ? `AI assist on · ${data.model}` : 'Built-in rules (AI assist off)'}
    </span>
  );
}
