import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiPath, csrfHandler, problem, server } from '../test/server';
import { api, ApiError } from './api';
import { safeRedirect } from './safeRedirect';

describe('api client', () => {
  it('fetches a CSRF token before the first state-changing request and echoes it in the header', async () => {
    let receivedToken: string | null = null;
    server.use(
      csrfHandler('token-abc'),
      http.post(apiPath('/things'), ({ request }) => {
        receivedToken = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json({ ok: true }, { status: 201 });
      }),
    );

    await expect(api.post('/things', { name: 'x' })).resolves.toEqual({ ok: true });
    expect(receivedToken).toBe('token-abc');
  });

  it('refreshes the token and retries once when the server reports CSRF_INVALID', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/';
    const seen: (string | null)[] = [];
    server.use(
      csrfHandler('fresh'),
      http.post(apiPath('/things'), ({ request }) => {
        const token = request.headers.get('X-XSRF-TOKEN');
        seen.push(token);
        return token === 'fresh'
          ? new HttpResponse(null, { status: 204 })
          : problem(403, 'CSRF_INVALID', 'Missing or invalid CSRF token.');
      }),
    );

    await expect(api.post('/things')).resolves.toBeUndefined();
    expect(seen).toEqual(['stale', 'fresh']);
  });

  it('turns problem+json responses into ApiError with code and field errors', async () => {
    server.use(
      http.post(apiPath('/things'), () =>
        problem(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', {
          fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
        }),
      ),
    );

    const error = await api.post('/things', {}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'One or more fields are invalid.',
      fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
    });
  });

  it('does not send a CSRF header on GET requests', async () => {
    let header: string | null = 'unset';
    server.use(
      http.get(apiPath('/things'), ({ request }) => {
        header = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json([]);
      }),
    );

    await api.get('/things');
    expect(header).toBeNull();
  });
});

describe('safeRedirect', () => {
  it.each([
    ['/account', '/account'],
    ['/a?b=c', '/a?b=c'],
    ['//evil.example', '/'],
    ['/\\evil.example', '/'],
    ['https://evil.example', '/'],
    [null, '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected);
  });
});
