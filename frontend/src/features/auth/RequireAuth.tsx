import { Navigate, Outlet, useLocation } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { PageSpinner } from '../../components/ui/Spinner';
import { useCurrentUser } from './hooks';
import type { Role } from './types';

/**
 * Layout route guard. Anonymous visitors are sent to /login and returned afterwards.
 * This is a UX convenience only: the backend enforces every authorization rule.
 */
export function RequireAuth({ role }: { role?: Role }) {
  const location = useLocation();
  const { data: user, isPending, isError } = useCurrentUser();

  if (isPending) return <PageSpinner />;
  if (isError) {
    return (
      <div className="mx-auto max-w-xl px-4 py-12">
        <Alert tone="error">We could not verify your session. Please refresh the page.</Alert>
      </div>
    );
  }
  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  if (role && !user.roles.includes(role)) {
    return (
      <div className="mx-auto max-w-xl px-4 py-12">
        <Alert tone="error">You do not have access to this page.</Alert>
      </div>
    );
  }
  return <Outlet />;
}
