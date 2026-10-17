import { Star } from 'lucide-react';

export function Rating({ value, count, size = 'sm' }: { value: number; count?: number; size?: 'sm' | 'md' }) {
  const starSize = size === 'sm' ? 'size-3.5' : 'size-4.5';
  return (
    <div className="flex items-center gap-1.5" aria-label={`Rated ${value.toFixed(1)} out of 5${count ? ` from ${count} reviews` : ''}`}>
      <div className="flex" aria-hidden="true">
        {[1, 2, 3, 4, 5].map((i) => {
          const fill = Math.max(0, Math.min(1, value - (i - 1)));
          return (
            <span key={i} className="relative">
              <Star className={`${starSize} text-slate-300`} fill="currentColor" strokeWidth={0} />
              <span className="absolute inset-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
                <Star className={`${starSize} text-accent-500`} fill="currentColor" strokeWidth={0} />
              </span>
            </span>
          );
        })}
      </div>
      <span className="text-xs font-medium text-slate-600" aria-hidden="true">
        {value.toFixed(1)}
        {count !== undefined && <span className="text-slate-400"> ({count.toLocaleString('en-US')})</span>}
      </span>
    </div>
  );
}
