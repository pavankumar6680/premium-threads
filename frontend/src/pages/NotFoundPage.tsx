import { Link } from 'react-router';
import { buttonClasses } from '../components/ui/Button';

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <p className="text-sm font-semibold text-brand-600">404</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Page not found</h1>
      <p className="mt-3 text-slate-600">The page you are looking for does not exist or has moved.</p>
      <Link to="/" className={buttonClasses('primary', 'md', 'mt-8')}>
        Back to home
      </Link>
    </div>
  );
}
