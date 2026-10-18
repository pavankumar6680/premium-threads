import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { User } from '../auth/types';
import type { Order } from '../orders/api';
import { ada } from '../../test/fixtures';
import { renderApp } from '../../test/renderApp';
import { apiPath, problem, server } from '../../test/server';
import type { AdminCategory, AdminOrder, AdminProduct, AdminProductRow, AnalyticsSummary, RuleImpact } from './api';

const grace: User = { id: 2, email: 'grace@example.com', fullName: 'Grace Hopper', roles: ['CUSTOMER', 'ADMIN'] };

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });

const monitors: AdminCategory = {
  id: 7, slug: 'monitors', name: 'Monitors', description: null, departmentSlug: 'home-office', departmentName: 'Home office',
  leaf: true, productCount: 12,
  attributes: [
    { key: 'inputs', label: 'Inputs', type: 'LIST', unit: null, filterable: true },
    { key: 'weight_kg', label: 'Weight', type: 'NUMBER', unit: 'kg', filterable: false },
  ],
};
const homeOffice: AdminCategory = { ...monitors, id: 1, slug: 'home-office', name: 'Home office', departmentSlug: null, departmentName: null, leaf: false, attributes: [] };

const row: AdminProductRow = {
  id: 31, sku: 'MON-001', slug: 'viewmax-27', name: 'ViewMax 27', brand: 'Viewmax', price: 249, active: true, qualityTier: 2,
  ratingAvg: 4.5, ratingCount: 120, categorySlug: 'monitors', categoryName: 'Monitors', stock: 3, updatedAt: '2026-10-17T10:00:00Z',
};

const product: AdminProduct = {
  id: 31, sku: 'MON-001', slug: 'viewmax-27', name: 'ViewMax 27', brand: 'Viewmax', description: 'A monitor', price: 249,
  qualityTier: 2, active: true, categorySlug: 'monitors', stock: 3, tags: ['office'],
  attributes: { inputs: ['HDMI', 'USB-C'], weight_kg: ['4.2'] }, ratingAvg: 4.5, ratingCount: 120, version: 4,
  updatedAt: '2026-10-17T10:00:00Z',
};

const summary: AnalyticsSummary = {
  days: 30, from: '2026-09-17T00:00:00Z',
  sales: { revenue: 12450.5, previousRevenue: 10000, orders: 41, cancelledOrders: 2, averageOrderValue: 303.67, unitsSold: 97 },
  daily: Array.from({ length: 30 }, (_, i) => ({ date: `2026-10-${String((i % 28) + 1).padStart(2, '0')}`, revenue: i * 10, orders: i % 3 })),
  ordersByStatus: { PAID: 5, SHIPPED: 10, DELIVERED: 26, CANCELLED: 2 },
  topProducts: [{ productId: 31, name: 'ViewMax 27', sku: 'MON-001', units: 14, revenue: 3486 }],
  inventory: { activeProducts: 140, lowStock: 6, outOfStock: 1, lowest: [{ productId: 31, name: 'ViewMax 27', sku: 'MON-001', stock: 3 }] },
  goals: { created: 88, describedInOwnWords: 30, averageBudget: 1350, byTemplate: [{ title: 'Home office', goals: 40 }] },
  recommendations: {
    runs: 120, infeasibleRuns: 6, infeasibleShare: 0.05, averageDurationMs: 35, p95DurationMs: 140, averageNodes: 5400,
    aiOverviews: 4, savedBundles: 18, customizedBundles: 9,
  },
  customers: { total: 64, newInPeriod: 12 },
};

/** The admin area is a lazily loaded chunk, so its first render can take longer than the default 1 s. */
const lazyChunk = { timeout: 5000 };

const asAdmin = () => server.use(http.get(apiPath('/auth/me'), () => HttpResponse.json(grace)));

describe('admin area', () => {
  it('is closed to customers', async () => {
    server.use(http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)));
    renderApp('/admin');
    expect(await screen.findByText('You do not have access to this page.', {}, lazyChunk)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Store admin' })).not.toBeInTheDocument();
  });

  it('shows the dashboard with revenue, its trend and low stock', async () => {
    asAdmin();
    server.use(http.get(apiPath('/admin/analytics/summary'), () => HttpResponse.json(summary)));
    const { user } = renderApp('/admin');

    expect(await screen.findByText('$12,450.50', {}, lazyChunk)).toBeInTheDocument();
    expect(screen.getByText('25% vs previous 30 days')).toBeInTheDocument();
    expect(screen.getByText(/6 of 140 active products have 5 or fewer units/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Store admin' })).toHaveAttribute('href', '/admin');

    let requested = '';
    server.use(
      http.get(apiPath('/admin/analytics/summary'), ({ request }) => {
        requested = new URL(request.url).search;
        return HttpResponse.json({ ...summary, days: 7 });
      }),
    );
    await user.click(screen.getByRole('button', { name: '7 days' }));
    await screen.findByText('25% vs previous 7 days');
    expect(requested).toBe('?days=7');
  });

  it('adjusts stock and takes products off sale from the list', async () => {
    asAdmin();
    let current = row;
    const calls: string[] = [];
    server.use(
      http.get(apiPath('/admin/categories'), () => HttpResponse.json([homeOffice, monitors])),
      http.get(apiPath('/admin/products'), () => HttpResponse.json(page([current]))),
      http.put(apiPath('/admin/products/31/stock'), async ({ request }) => {
        const { quantity } = (await request.json()) as { quantity: number };
        calls.push(`stock=${quantity}`);
        current = { ...current, stock: quantity };
        return HttpResponse.json({ ...product, stock: quantity });
      }),
      http.patch(apiPath('/admin/products/31/status'), async ({ request }) => {
        const { active } = (await request.json()) as { active: boolean };
        calls.push(`active=${active}`);
        current = { ...current, active };
        return HttpResponse.json({ ...product, active });
      }),
    );
    const { user } = renderApp('/admin/products');

    const stock = await screen.findByRole('spinbutton', { name: 'Stock for ViewMax 27' }, lazyChunk);
    expect(screen.getByText('Low')).toBeInTheDocument();
    await user.clear(stock);
    await user.type(stock, '40');
    await user.click(screen.getByRole('button', { name: 'Save stock for ViewMax 27' }));
    expect(await screen.findByText('Stock for ViewMax 27 set to 40.')).toBeInTheDocument();

    await user.click(screen.getByRole('switch', { name: 'ViewMax 27 on sale' }));
    expect(await screen.findByText('ViewMax 27 is off sale.')).toBeInTheDocument();
    expect(calls).toEqual(['stock=40', 'active=false']);
  });

  it('edits specifications with optimistic locking and explains conflicts', async () => {
    asAdmin();
    let sent: Record<string, unknown> | null = null;
    server.use(
      http.get(apiPath('/admin/categories'), () => HttpResponse.json([homeOffice, monitors])),
      http.get(apiPath('/admin/products/31'), () => HttpResponse.json(product)),
      http.put(apiPath('/admin/products/31'), async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return problem(409, 'STALE_PRODUCT', 'Someone else changed this product after you opened it.', { currentVersion: 5 });
      }),
    );
    const { user } = renderApp('/admin/products/31');

    const inputs = await screen.findByRole('textbox', { name: 'Inputs' }, lazyChunk);
    expect(inputs).toHaveValue('HDMI, USB-C');
    await user.clear(inputs);
    await user.type(inputs, 'HDMI, DisplayPort, ');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText(/Someone else changed this product/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload the latest version' })).toBeInTheDocument();
    expect(sent).toMatchObject({ version: 4, attributes: { inputs: ['HDMI', 'DisplayPort'], weight_kg: ['4.2'] } });
  });

  it('ships an order and asks for confirmation before cancelling', async () => {
    asAdmin();
    const order: Order = {
      id: 42, orderNumber: 'BMB-7K3Q9X2M', status: 'PAID', createdAt: '2026-10-17T12:00:00Z', cancelledAt: null,
      items: [{ productId: 31, slug: 'viewmax-27', sku: 'MON-001', name: 'ViewMax 27', brand: 'Viewmax', unitPrice: 249, quantity: 1, lineTotal: 249 }],
      subtotal: 249, shipping: 0, tax: 19.92, total: 268.92, currency: 'USD',
      shippingAddress: { fullName: 'Ada Lovelace', line1: '12 Analytical Way', line2: null, city: 'London', state: 'LDN', postalCode: 'N1 9GU', country: 'GB', phone: null },
      payment: { reference: 'SIM-1', method: 'SIMULATED_CARD', status: 'APPROVED', amount: 268.92 },
      cancellable: true,
    };
    let state: AdminOrder = { order, customer: { id: 1, email: 'ada@example.com', fullName: 'Ada Lovelace' }, allowedTransitions: ['SHIPPED', 'CANCELLED'] };
    server.use(
      http.get(apiPath('/admin/orders/42'), () => HttpResponse.json(state)),
      http.post(apiPath('/admin/orders/42/status'), async ({ request }) => {
        const { status } = (await request.json()) as { status: 'SHIPPED' };
        state = { ...state, order: { ...order, status, cancellable: false }, allowedTransitions: ['DELIVERED'] };
        return HttpResponse.json(state);
      }),
    );
    const { user } = renderApp('/admin/orders/42');

    await user.click(await screen.findByRole('button', { name: 'Cancel and refund' }, lazyChunk));
    expect(screen.getByText(/The customer is refunded \$268.92/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm cancellation' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Mark as shipped' }));
    expect(await screen.findByText('Order marked as shipped.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Mark as delivered' })).toBeInTheDocument();
    expect(within(screen.getByRole('heading', { level: 1 })).getByText('Shipped')).toBeInTheDocument();
  });

  it('previews a compatibility rule before saving it', async () => {
    asAdmin();
    const arms: AdminCategory = { ...monitors, id: 8, slug: 'monitor-arms', name: 'Monitor arms', attributes: [{ key: 'max_load_kg', label: 'Max load', type: 'NUMBER', unit: 'kg', filterable: true }] };
    const impact: RuleImpact = { evaluated: 60, violating: 12, notApplicable: 4, examples: ['ViewMax 27 × LiteArm'], summary: '12 of 60 product pairs would be blocked; 4 pairs lack the attributes and are not affected.' };
    let previewed: Record<string, unknown> | null = null;
    server.use(
      http.get(apiPath('/admin/categories'), () => HttpResponse.json([homeOffice, monitors, arms])),
      http.get(apiPath('/admin/compatibility-rules'), () => HttpResponse.json([])),
      http.post(apiPath('/admin/compatibility-rules/preview'), async ({ request }) => {
        previewed = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(impact);
      }),
    );
    const { user } = renderApp('/admin/rules');

    await user.click(await screen.findByRole('button', { name: 'New rule' }, lazyChunk));
    await user.type(screen.getByRole('textbox', { name: 'Code' }), 'arm_load');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Kind of check' }), 'LTE');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Source category' }), 'monitors');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Source attribute' }), 'weight_kg');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target category' }), 'monitor-arms');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target attribute' }), 'max_load_kg');
    await user.click(screen.getByRole('button', { name: 'Preview impact' }));

    expect(await screen.findByText(impact.summary)).toBeInTheDocument();
    expect(screen.getByText('ViewMax 27 × LiteArm')).toBeInTheDocument();
    expect(previewed).toMatchObject({
      code: 'ARM_LOAD', operator: 'LTE', sourceCategory: 'monitors', sourceAttr: 'weight_kg',
      targetCategory: 'monitor-arms', targetAttr: 'max_load_kg', value: null,
    });
  });
});
