import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { User } from '../auth/types';
import type { Order } from '../orders/api';
import { ada } from '../../test/fixtures';
import { renderApp } from '../../test/renderApp';
import { apiPath, problem, server } from '../../test/server';
import type { AdminBrand, AdminCategory, AdminOrder, AdminProduct, AdminProductRow, AnalyticsSummary, RuleImpact } from './api';

const grace: User = { id: 2, email: 'grace@example.com', fullName: 'Grace Hopper', roles: ['CUSTOMER', 'ADMIN'] };

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });

const shirts: AdminCategory = {
  id: 7, slug: 'men-shirts', name: 'Shirts', description: null, departmentSlug: 'men', departmentName: 'Men',
  leaf: true, productCount: 12,
  attributes: [
    { key: 'fit', label: 'Fit', type: 'TEXT', unit: null, filterable: true },
    { key: 'formality', label: 'Formality', type: 'NUMBER', unit: null, filterable: false },
  ],
};
const men: AdminCategory = { ...shirts, id: 1, slug: 'men', name: 'Men', departmentSlug: null, departmentName: null, leaf: false, attributes: [] };

const row: AdminProductRow = {
  id: 31, sku: 'LEV-010', slug: 'levis-oxford-shirt', name: 'Oxford Shirt', brand: "Levi's", price: 59, active: true, qualityTier: 2,
  ratingAvg: 4.5, ratingCount: 120, categorySlug: 'men-shirts', categoryName: 'Shirts', stock: 3, variantCount: 1, compareAtPrice: null, gender: 'MEN', featured: false, updatedAt: '2026-10-17T10:00:00Z',
};

const product: AdminProduct = {
  id: 31, sku: 'LEV-010', slug: 'levis-oxford-shirt', name: 'Oxford Shirt', brand: "Levi's", brandSlug: 'levis', description: 'A shirt', price: 59,
  compareAtPrice: null, qualityTier: 2, active: true, categorySlug: 'men-shirts', gender: 'MEN', featured: false, newArrival: false, trending: false,
  material: '100% cotton', care: 'Machine wash cold',
  variants: [
    { id: 301, sku: 'LEV-010-WHIT-M', size: 'M', color: 'White', colorHex: '#FFFFFF', colorFamily: 'NEUTRAL', active: true, stock: 2 },
    { id: 302, sku: 'LEV-010-WHIT-L', size: 'L', color: 'White', colorHex: '#FFFFFF', colorFamily: 'NEUTRAL', active: true, stock: 1 },
  ],
  styles: ['formal'], occasions: ['office'], stock: 3, tags: ['office'],
  attributes: { fit: ['Regular'], formality: ['4'] }, ratingAvg: 4.5, ratingCount: 120, version: 4,
  updatedAt: '2026-10-17T10:00:00Z',
};

const levis: AdminBrand = { id: 3, slug: 'levis', name: "Levi's", description: '', featured: true, active: true, productCount: 9 };

const summary: AnalyticsSummary = {
  days: 30, from: '2026-09-17T00:00:00Z',
  sales: { revenue: 12450.5, previousRevenue: 10000, orders: 41, cancelledOrders: 2, averageOrderValue: 303.67, unitsSold: 97 },
  daily: Array.from({ length: 30 }, (_, i) => ({ date: `2026-10-${String((i % 28) + 1).padStart(2, '0')}`, revenue: i * 10, orders: i % 3 })),
  ordersByStatus: { PAID: 5, SHIPPED: 10, DELIVERED: 26, CANCELLED: 2 },
  topProducts: [{ productId: 31, name: 'Oxford Shirt', sku: 'MON-001', units: 14, revenue: 3486 }],
  inventory: { activeProducts: 140, lowStock: 6, outOfStock: 1, lowest: [{ productId: 31, name: 'Oxford Shirt', sku: 'MON-001', stock: 3 }] },
  goals: { created: 88, describedInOwnWords: 30, averageBudget: 1350, byTemplate: [{ title: 'Home office', goals: 40 }] },
  recommendations: {
    runs: 120, infeasibleRuns: 6, infeasibleShare: 0.05, averageDurationMs: 35, p95DurationMs: 140, averageNodes: 5400,
    aiOverviews: 4, savedBundles: 18, customizedBundles: 9,
  },
  customers: { total: 64, newInPeriod: 12 },
};

/** The admin area is a lazily loaded chunk, so its first render can take longer than the default 1 s. */
const lazyChunk = { timeout: 15_000 };

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
      http.get(apiPath('/admin/categories'), () => HttpResponse.json([men, shirts])),
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

    const stock = await screen.findByRole('spinbutton', { name: 'Stock for Oxford Shirt' }, lazyChunk);
    expect(screen.getByText('Low')).toBeInTheDocument();
    await user.clear(stock);
    await user.type(stock, '40');
    await user.click(screen.getByRole('button', { name: 'Save stock for Oxford Shirt' }));
    expect(await screen.findByText('Stock for Oxford Shirt set to 40.')).toBeInTheDocument();

    await user.click(screen.getByRole('switch', { name: 'Oxford Shirt on sale' }));
    expect(await screen.findByText('Oxford Shirt is off sale.')).toBeInTheDocument();
    expect(calls).toEqual(['stock=40', 'active=false']);
  });

  it('edits variants and merchandising, with optimistic locking', async () => {
    asAdmin();
    let sent: Record<string, unknown> | null = null;
    server.use(
      http.get(apiPath('/admin/categories'), () => HttpResponse.json([men, shirts])),
      http.get(apiPath('/admin/products/31'), () => HttpResponse.json(product)),
      http.get(apiPath('/admin/brands'), () => HttpResponse.json([levis])),
      http.get(apiPath('/admin/products/31/images'), () => HttpResponse.json([])),
      http.put(apiPath('/admin/products/31'), async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return problem(409, 'STALE_PRODUCT', 'Someone else changed this product after you opened it.', { currentVersion: 5 });
      }),
    );
    const { user } = renderApp('/admin/products/31');

    const fit = await screen.findByRole('textbox', { name: 'Fit' }, lazyChunk);
    expect(fit).toHaveValue('Regular');
    await user.clear(fit);
    await user.type(fit, 'Slim');
    await user.click(screen.getByRole('checkbox', { name: 'Trending' }));
    // a new colorway in every letter size
    await user.type(screen.getByRole('textbox', { name: 'Add a colorway' }), 'Navy');
    await user.click(screen.getByRole('button', { name: /add in 6 sizes/i }));
    expect(screen.getAllByRole('textbox', { name: 'Color' })).toHaveLength(8);
    await user.click(screen.getByRole('checkbox', { name: 'L White on sale' }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText(/Someone else changed this product/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload the latest version' })).toBeInTheDocument();
    expect(sent).toMatchObject({ version: 4, brand: "Levi's", trending: true, styles: ['formal'], attributes: { fit: ['Slim'], formality: ['4'] } });
    const variants = (sent as unknown as { variants: { id?: number; size: string; color: string; active: boolean; stock?: number }[] }).variants;
    expect(variants).toHaveLength(8);
    expect(variants[1]).toEqual(expect.objectContaining({ id: 302, active: false }));
    expect(variants[1]).not.toHaveProperty('stock');
    expect(variants.filter((v) => v.color === 'Navy').map((v) => v.size)).toEqual(['XS', 'S', 'M', 'L', 'XL', 'XXL']);
  });

  it('ships an order and asks for confirmation before cancelling', async () => {
    asAdmin();
    const order: Order = {
      id: 42, orderNumber: 'BMB-7K3Q9X2M', status: 'PAID', createdAt: '2026-10-17T12:00:00Z', cancelledAt: null,
      items: [{ productId: 31, variantId: 301, size: null, color: null, slug: 'viewmax-27', sku: 'MON-001', name: 'Oxford Shirt', brand: 'Viewmax', unitPrice: 249, quantity: 1, lineTotal: 249 }],
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

  it('previews an outfit rule that applies to every piece', async () => {
    asAdmin();
    const impact: RuleImpact = { evaluated: 60, violating: 12, notApplicable: 4, examples: ['Oxford Shirt × Track Pants'], summary: '12 of 60 product pairs would be blocked; 4 pairs lack the attributes and are not affected.' };
    let previewed: Record<string, unknown> | null = null;
    server.use(
      http.get(apiPath('/admin/categories'), () => HttpResponse.json([men, shirts])),
      http.get(apiPath('/admin/compatibility-rules'), () => HttpResponse.json([])),
      http.post(apiPath('/admin/compatibility-rules/preview'), async ({ request }) => {
        previewed = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(impact);
      }),
    );
    const { user } = renderApp('/admin/rules');

    await user.click(await screen.findByRole('button', { name: 'New rule' }, lazyChunk));
    await user.type(screen.getByRole('textbox', { name: 'Code' }), 'dress_code');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Kind of check' }), 'MAX_DIFFERENCE');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Source category' }), '*');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Source attribute' }), 'dress_code');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target category' }), '*');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target attribute' }), 'dress_code');
    await user.type(screen.getByRole('spinbutton', { name: 'Largest difference' }), '2');
    await user.click(screen.getByRole('button', { name: 'Preview impact' }));

    expect(await screen.findByText(impact.summary)).toBeInTheDocument();
    expect(screen.getByText('Oxford Shirt × Track Pants')).toBeInTheDocument();
    expect(previewed).toMatchObject({
      code: 'DRESS_CODE', operator: 'MAX_DIFFERENCE', sourceCategory: '*', sourceAttr: 'dress_code',
      targetCategory: '*', targetAttr: 'dress_code', value: '2',
    });
  });

  it('adds a brand and features it on the homepage', async () => {
    asAdmin();
    let brands = [levis];
    const sent: unknown[] = [];
    server.use(
      http.get(apiPath('/admin/brands'), () => HttpResponse.json(brands)),
      http.post(apiPath('/admin/brands'), async ({ request }) => {
        const body = (await request.json()) as { name: string; featured: boolean };
        sent.push(body);
        const created: AdminBrand = { id: 9, slug: 'arket', name: body.name, description: '', featured: body.featured, active: true, productCount: 0 };
        brands = [...brands, created];
        return HttpResponse.json(created, { status: 201 });
      }),
    );
    const { user } = renderApp('/admin/brands');

    await user.click(await screen.findByRole('button', { name: 'New brand' }, lazyChunk));
    await user.type(screen.getByRole('textbox', { name: /^name/i }), 'Arket');
    await user.click(screen.getByRole('checkbox', { name: 'Featured on the homepage' }));
    await user.click(screen.getByRole('button', { name: 'Create brand' }));

    expect(await screen.findByText('Brand created.')).toBeInTheDocument();
    expect(await screen.findByRole('switch', { name: 'Feature Arket' })).toHaveAttribute('aria-checked', 'true');
    expect(sent).toEqual([expect.objectContaining({ name: 'Arket', featured: true, active: true })]);
  });
});
