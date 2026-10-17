import clsx from 'clsx';

export const LOW_STOCK_THRESHOLD = 5;

export function StockBadge({ available, active = true, className }: {
  available: number;
  active?: boolean;
  className?: string;
}) {
  const [label, tone] = !active
    ? ['Discontinued', 'text-slate-500']
    : available === 0
      ? ['Out of stock', 'text-red-600']
      : available <= LOW_STOCK_THRESHOLD
        ? [`Only ${available} left`, 'text-amber-700']
        : ['In stock', 'text-emerald-700'];
  return <p className={clsx('text-xs font-semibold', tone, className)}>{label}</p>;
}
