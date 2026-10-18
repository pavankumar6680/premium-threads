import clsx from 'clsx';
import { Link } from 'react-router';

/** Wordmark: an editorial serif with a coral accent dot. */
export function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className={clsx('flex items-baseline gap-0.5 whitespace-nowrap', light ? 'text-white' : 'text-slate-900')} aria-label="Premium Threads home">
      <span className="font-display text-[1.45rem] font-semibold leading-none tracking-tight">
        Premium <em className="font-medium italic text-brand-500">Threads</em>
      </span>
      <span className="size-1.5 rounded-full bg-brand-500" aria-hidden="true" />
    </Link>
  );
}
