import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../../test/renderApp';
import { apiPath, problem, server } from '../../test/server';
import type { User } from './types';

const ada: User = { id: 1, email: 'ada@example.com', fullName: 'Ada Lovelace', roles: ['CUSTOMER'] };

describe('authentication flows', () => {
  it('redirects anonymous visitors from a protected page to login and back after signing in', async () => {
    let signedIn = false;
    server.use(
      http.get(apiPath('/auth/me'), () =>
        signedIn ? HttpResponse.json(ada) : problem(401, 'UNAUTHENTICATED', 'Sign in'),
      ),
      http.post(apiPath('/auth/login'), async ({ request }) => {
        const body = (await request.json()) as { email: string; password: string };
        if (body.email !== ada.email || body.password !== 'Passw0rd!') {
          return problem(401, 'INVALID_CREDENTIALS', 'Invalid email or password.');
        }
        signedIn = true;
        return HttpResponse.json(ada);
      }),
    );

    const { user } = renderApp('/account');

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'Passw0rd!');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(screen.getAllByText('Ada Lovelace').length).toBeGreaterThan(0);
  });

  it('shows client-side validation errors without calling the API', async () => {
    const { user } = renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('shows the server message when credentials are rejected', async () => {
    server.use(
      http.post(apiPath('/auth/login'), () => problem(401, 'INVALID_CREDENTIALS', 'Invalid email or password.')),
    );
    const { user } = renderApp('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'nope12345');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
  });

  it('maps server field errors onto the registration form', async () => {
    server.use(
      http.post(apiPath('/auth/register'), () =>
        problem(409, 'EMAIL_TAKEN', 'An account with this email already exists.'),
      ),
    );
    const { user } = renderApp('/register');

    await user.type(await screen.findByLabelText('Full name'), 'Ada Lovelace');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'Passw0rd1');
    await user.type(screen.getByLabelText('Confirm password'), 'Passw0rd2');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Confirm password'));
    await user.type(screen.getByLabelText('Confirm password'), 'Passw0rd1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists.');
  });

  it('signs out from the header', async () => {
    let signedIn = true;
    server.use(
      http.get(apiPath('/auth/me'), () =>
        signedIn ? HttpResponse.json(ada) : problem(401, 'UNAUTHENTICATED', 'Sign in'),
      ),
      http.post(apiPath('/auth/logout'), () => {
        signedIn = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: /sign out/i }));

    expect(await screen.findByRole('link', { name: 'Create account' })).toBeInTheDocument();
    expect(signedIn).toBe(false);
  });
});
