import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { emptyCart, emptyPage } from './fixtures';

/** Matches the API path on any origin. */
export const apiPath = (path: string) => `*/api/v1${path}`;

/** Simulates the backend issuing the readable XSRF-TOKEN cookie. */
export const csrfHandler = (token = 'csrf-token-1') =>
  http.get(apiPath('/auth/csrf'), () => {
    document.cookie = `XSRF-TOKEN=${token}; path=/`;
    return new HttpResponse(null, { status: 204 });
  });

export const problem = (status: number, code: string, detail: string, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ status, code, detail, title: 'Error', ...extra }, { status });

/** Baseline: anonymous visitor and an empty store. Tests override what they need with server.use(). */
export const defaultHandlers = [
  csrfHandler(),
  http.get(apiPath('/auth/me'), () => problem(401, 'UNAUTHENTICATED', 'Sign in')),
  http.get(apiPath('/categories'), () => HttpResponse.json([])),
  http.get(apiPath('/products/facets'), () =>
    HttpResponse.json({ brands: [], minPrice: null, maxPrice: null, attributes: [] }),
  ),
  http.get(apiPath('/products'), () => HttpResponse.json(emptyPage)),
  http.get(apiPath('/cart'), () => HttpResponse.json(emptyCart)),
  http.get(apiPath('/wishlist'), () => HttpResponse.json([])),
  http.get(apiPath('/goal-templates'), () => HttpResponse.json([])),
  http.get(apiPath('/goals/:id/recommendations/latest'), () => problem(404, 'NO_RECOMMENDATIONS', 'None yet')),
  http.get(apiPath('/ai/status'), () =>
    HttpResponse.json({ enabled: false, available: false, model: 'llama3.2:3b', message: 'AI assistance is off.' }),
  ),
];

export const server = setupServer(...defaultHandlers);
