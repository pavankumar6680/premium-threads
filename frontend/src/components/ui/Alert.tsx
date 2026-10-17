import clsx from 'clsx';
import { CircleAlert, CircleCheck, Info } from 'lucide-react';
import type { ReactNode } from 'react';

type Tone = 'error' | 'success' | 'info';

const tones: Record<Tone, { box: string; Icon: typeof Info }> = {
  error: { box: 'border-red-200 bg-red-50 text-red-800', Icon: CircleAlert },
  success: { box: 'border-emerald-200 bg-emerald-50 text-emerald-800', Icon: CircleCheck },
  info: { box: 'border-brand-200 bg-brand-50 text-brand-900', Icon: Info },
};

export function Alert({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  const { box, Icon } = tones[tone];
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={clsx('flex gap-2 rounded-lg border p-3 text-sm', box)}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>{children}</div>
    </div>
  );
}
