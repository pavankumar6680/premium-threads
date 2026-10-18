import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Address } from '../account/addresses';
import type { Cart, CartItem } from '../cart/api';
import type { Order } from '../orders/api';
import { ada, emptyCart } from '../../test/fixtures';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';

const line: CartItem = {
  productId: 10, variantId: 10, size: 'One size', color: 'Standard', colorHex: null, sku: 'DSK-003', slug: 'ergolift-flexistand-desk-120', name: 'Ergolift FlexiStand Desk 120', brand: 'Ergolift',
  categorySlug: 'desks', unitPrice: 329, quantity: 1, lineTotal: 329, availableQuantity: 30, available: true,
};

const cartWith = (quantity: number): Cart => {
  const subtotal = 329 * quantity;
  const tax = Math.round(subtotal * 8) / 100;
  return { ...emptyCart, items: [{ ...line, quantity, lineTotal: subtotal }], itemCount: quantity, subtotal, tax, total: subtotal + tax, checkoutReady: true };
};

const home: Address = {
  id: 5, fullName: 'Ada Lovelace', line1: '12 Analytical Way', line2: null, city: 'London', state: 'LDN',
  postalCode: 'N1 9GU', country: 'GB', phone: null, defaultAddress: true,
};

const order: Order = {
  id: 42, orderNumber: 'BMB-7K3Q9X2M', status: 'PAID', createdAt: '2026-10-17T12:00:00Z', cancelledAt: null,
  items: [{ productId: 10, variantId: 10, size: null, color: null, slug: line.slug, sku: line.sku, name: line.name, brand: line.brand, unitPrice: 329, quantity: 1, lineTotal: 329 }],
  subtotal: 329, shipping: 0, tax: 26.32, total: 355.32, currency: 'USD',
  shippingAddress: { fullName: home.fullName, line1: home.line1, line2: null, city: home.city, state: home.state, postalCode: home.postalCode, country: home.country, phone: null },
  payment: { reference: 'SIM-ABC123', method: 'SIMULATED_CARD', status: 'APPROVED', amount: 355.32 },
  cancellable: true,
};

describe('cart and checkout', () => {
  it('shows server-computed totals and updates quantities through the API', async () => {
    let cart = cartWith(1);
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/cart'), () => HttpResponse.json(cart)),
      http.put(apiPath('/cart/items/10'), async ({ request }) => {
        const { quantity } = (await request.json()) as { quantity: number };
        cart = cartWith(quantity);
        return HttpResponse.json(cart);
      }),
    );
    const { user } = renderApp('/cart');

    expect(await screen.findByRole('heading', { name: 'Shopping cart' })).toBeInTheDocument();
    expect(screen.getByText('$355.32')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Increase quantity' }));

    expect(await screen.findByText('$710.64')).toBeInTheDocument(); // 658.00 + 52.64 tax
    expect(screen.getByRole('link', { name: 'Proceed to checkout' })).toHaveAttribute('href', '/checkout');
  });

  it('places the order with an idempotency key and shows the confirmation', async () => {
    let placed = false;
    let idempotencyKey: string | null = null;
    let body: unknown;
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/cart'), () => HttpResponse.json(placed ? emptyCart : cartWith(1))),
      http.get(apiPath('/me/addresses'), () => HttpResponse.json([home])),
      http.post(apiPath('/checkout'), async ({ request }) => {
        idempotencyKey = request.headers.get('Idempotency-Key');
        body = await request.json();
        placed = true;
        return HttpResponse.json(order, { status: 201 });
      }),
      http.get(apiPath('/orders/42'), () => HttpResponse.json(order)),
    );
    const { user } = renderApp('/checkout');

    expect(await screen.findByRole('radio', { name: /Ada Lovelace/ })).toBeChecked();
    await user.click(screen.getByRole('button', { name: /place order/i }));

    expect(await screen.findByRole('heading', { name: 'Thank you! Your order is confirmed.' })).toBeInTheDocument();
    expect(location.current.pathname).toBe('/orders/42');
    expect(screen.getByText('SIM-ABC123')).toBeInTheDocument();
    expect(body).toEqual({ addressId: 5 });
    expect(idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('asks for an address before an order can be placed', async () => {
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/cart'), () => HttpResponse.json(cartWith(1))),
      http.get(apiPath('/me/addresses'), () => HttpResponse.json([])),
    );
    renderApp('/checkout');

    expect(await screen.findByLabelText('Street address')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /place order/i })).toBeDisabled();
  });

  it('explains stock problems reported by the server', async () => {
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/cart'), () => HttpResponse.json(cartWith(1))),
      http.get(apiPath('/me/addresses'), () => HttpResponse.json([home])),
      http.post(apiPath('/checkout'), () =>
        HttpResponse.json(
          { status: 409, code: 'INSUFFICIENT_STOCK', detail: 'Some items do not have enough stock.', shortages: [] },
          { status: 409 },
        ),
      ),
    );
    const { user } = renderApp('/checkout');

    await user.click(await screen.findByRole('button', { name: /place order/i }));

    expect(await screen.findByText(/Some items do not have enough stock/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Review your cart' })).toBeInTheDocument();
  });
});
