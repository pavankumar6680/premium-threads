import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
import { AppRoutes } from '../app/AppRoutes';
import { ToastProvider } from '../components/ui/Toast';
import { createQueryClient } from '../lib/queryClient';

/** Exposes the current location so tests can assert on navigation (path and query string). */
export const location = { current: { pathname: '', search: '' } };

function LocationProbe() {
  const loc = useLocation();
  location.current = { pathname: loc.pathname, search: loc.search };
  return null;
}

/** Renders the whole app (real routes, real query client) at the given URL. */
export function renderApp(initialPath = '/') {
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retry: false } });
  const user = userEvent.setup();
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <ToastProvider>
          <LocationProbe />
          <AppRoutes />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { user, queryClient, ...utils };
}
