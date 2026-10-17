import { Link } from 'react-router';

export function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2 font-extrabold tracking-tight text-slate-900">
      <svg viewBox="0 0 32 32" className="size-8" aria-hidden="true">
        <rect width="32" height="32" rx="8" className="fill-brand-600" />
        <path
          d="M9 12.5 16 9l7 3.5v7L16 23l-7-3.5zM9 12.5 16 16l7-3.5M16 16v7"
          fill="none"
          stroke="#fff"
          strokeWidth="2"
          strokeLinejoin="round"
        />
      </svg>
      <span className="text-lg">
        Build<span className="text-brand-600">My</span>Bundle
      </span>
    </Link>
  );
}
