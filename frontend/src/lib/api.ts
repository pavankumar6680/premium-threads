/**
 * Thin fetch wrapper for the BuildMyBundle API.
 *
 * - Sends the session cookie (same origin via the Vite/nginx proxy).
 * - CSRF: before a state-changing request it makes sure the XSRF-TOKEN cookie exists and echoes it in
 *   the X-XSRF-TOKEN header. If the server rejects the token (CSRF_INVALID, e.g. after login rotated it),
 *   it fetches a fresh one and retries once.
 * - Errors: RFC 9457 problem+json bodies become ApiError with a stable `code` and per-field errors.
 */

export const API_PREFIX = '/api/v1';
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export type FieldError = { field: string; message: string };

type ProblemDetail = {
  status?: number;
  title?: string;
  detail?: string;
  code?: string;
  fieldErrors?: FieldError[];
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  /** The full problem body, for endpoint-specific details (e.g. reasons, suggestions). */
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = [], details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.details = details;
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
};

export function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find((c) => c.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : null;
}

function apiUrl(path: string): string {
  return new URL(`${API_PREFIX}${path}`, window.location.origin).toString();
}

async function refreshCsrfToken(): Promise<void> {
  await fetch(apiUrl('/auth/csrf'), { credentials: 'include' });
}

function send(path: string, method: string, options: RequestOptions, mutating: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  // FormData (file uploads) sets its own multipart content type with the boundary
  const form = options.body instanceof FormData;
  if (options.body !== undefined && !form) headers['Content-Type'] = 'application/json';
  if (mutating) headers[CSRF_HEADER] = readCookie(CSRF_COOKIE) ?? '';
  return fetch(apiUrl(path), {
    method,
    headers,
    credentials: 'include',
    body: options.body === undefined ? undefined : form ? (options.body as FormData) : JSON.stringify(options.body),
    signal: options.signal,
  });
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const mutating = !SAFE_METHODS.has(method);

  if (mutating && !readCookie(CSRF_COOKIE)) await refreshCsrfToken();
  let response = await send(path, method, options, mutating);

  if (!response.ok) {
    let problem = await readProblem(response);
    if (mutating && response.status === 403 && problem?.code === 'CSRF_INVALID') {
      await refreshCsrfToken();
      response = await send(path, method, options, mutating);
      problem = response.ok ? null : await readProblem(response);
    }
    if (!response.ok) {
      throw new ApiError(
        response.status,
        problem?.code ?? `HTTP_${response.status}`,
        problem?.detail ?? (response.statusText || 'Request failed'),
        problem?.fieldErrors ?? [],
        (problem as Record<string, unknown> | null) ?? {},
      );
    }
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiRequest<T>(path, { ...options, method: 'DELETE' }),
};
