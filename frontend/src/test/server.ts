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
    HttpResponse.json({ brands: [], minPrice: null, maxPrice: null, sizes: [], colors: [], styles: [], attributes: [] }),
  ),
  http.get(apiPath('/brands'), () => HttpResponse.json([])),
  http.get(apiPath('/home/sections'), () => HttpResponse.json([])),
  http.get(apiPath('/vocabulary'), () => HttpResponse.json({ styles: [], occasions: [], colors: [{ name: 'Black', hex: '#111111', family: 'NEUTRAL' }, { name: 'Navy', hex: '#1F2A44', family: 'NEUTRAL' }, { name: 'Coral', hex: '#F04452', family: 'ACCENT' }], sizes: { ALPHA: ['S', 'M', 'L'], WAIST: ['30', '32', '34'] } })),
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
