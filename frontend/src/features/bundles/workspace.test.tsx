import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ada } from '../../test/fixtures';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { Bundle, BundleItem } from '../recommendations/api';
import type { Alternative } from './api';

const item = (overrides: Partial<BundleItem>): BundleItem => ({
  productId: 1, slug: 'p', sku: 'SKU', name: 'Product', brand: 'Brand', categorySlug: 'cameras', categoryName: 'Cameras',
  quantity: 1, unitPrice: 100, currentPrice: 100, lineTotal: 100, optional: false, locked: false,
  explanation: 'Explained.', ratingAvg: 4.5, ratingCount: 100, availableQuantity: 10, available: true, ...overrides,
});

const camera = item({ productId: 1, name: 'Fotonix FX-30', categorySlug: 'cameras', categoryName: 'Cameras', unitPrice: 699, currentPrice: 699, lineTotal: 699 });
const lens = item({ productId: 2, name: 'Fotonix FX 50mm', categorySlug: 'lenses', categoryName: 'Lenses', unitPrice: 249, currentPrice: 249, lineTotal: 249 });

const draft: Bundle = {
  id: 'draft-1', goalId: 'goal-1', goalTitle: 'Photography studio', runId: null, sourceBundleId: 'rec-1', tier: 'BALANCED',
  name: 'Balanced bundle', status: 'DRAFT', editable: true, budget: 3000, totalPrice: 948, currentTotal: 948,
  summary: null, items: [camera, lens],
  compatibility: { compatible: true, passed: [{ ruleCode: 'LENS_MOUNT', label: 'same lens mount', productIds: [2, 1] }], violations: [] },
  purchasable: true, createdAt: '2026-10-17T12:00:00Z', updatedAt: '2026-10-17T12:00:00Z',
};

const alternatives: Alternative[] = [
  { productId: 2, slug: 'p2', name: 'Fotonix FX 50mm', brand: 'Fotonix', price: 249, priceDelta: 0, ratingAvg: 4.7, ratingCount: 1900, availableQuantity: 25, inStock: true, compatible: true, issues: [], current: true },
  { productId: 5, slug: 'p5', name: 'Lenscraft 18-50mm', brand: 'Lenscraft', price: 449, priceDelta: 200, ratingAvg: 4.5, ratingCount: 870, availableQuantity: 14, inStock: true, compatible: true, issues: [], current: false },
  { productId: 3, slug: 'p3', name: 'Optima OM 35mm', brand: 'Optima', price: 599, priceDelta: 350, ratingAvg: 4.6, ratingCount: 410, availableQuantity: 10, inStock: true, compatible: false, issues: ["The lens does not fit the camera's lens mount."], current: false },
];

function workspaceEndpoints(current: { bundle: Bundle }) {
  server.use(
    http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
    http.get(apiPath('/bundles/draft-1'), () => HttpResponse.json(current.bundle)),
    http.get(apiPath('/bundles/draft-1/alternatives'), () => HttpResponse.json(alternatives)),
    http.get(apiPath('/bundles/draft-1/accessories'), () => HttpResponse.json([])),
  );
}

describe('bundle workspace', () => {
  it('swaps a product, showing price changes and compatibility before choosing', async () => {
    const current = { bundle: draft };
    let replacedWith: unknown;
    workspaceEndpoints(current);
    server.use(
      http.put(apiPath('/bundles/draft-1/items/lenses'), async ({ request }) => {
        replacedWith = await request.json();
        current.bundle = {
          ...draft, currentTotal: 1148, totalPrice: 1148,
          items: [camera, { ...lens, productId: 5, name: 'Lenscraft 18-50mm', currentPrice: 449, lineTotal: 449, explanation: 'Chosen by you.' }],
        };
        return HttpResponse.json(current.bundle);
      }),
    );
    const { user } = renderApp('/bundles/draft-1');

    expect(await screen.findByRole('heading', { name: 'Balanced bundle' })).toBeInTheDocument();
    expect(screen.getByText('Everything works together')).toBeInTheDocument();
    const lensRow = screen.getByText('Fotonix FX 50mm').closest('li')!;
    await user.click(within(lensRow).getByRole('button', { name: /swap/i }));

    const panel = await screen.findByRole('region', { name: 'Alternatives for Lenses' });
    expect(await within(panel).findByText('+$200.00')).toBeInTheDocument();
    expect(within(panel).getByText("The lens does not fit the camera's lens mount.")).toBeInTheDocument();
    expect(within(panel).getByText('Current')).toBeInTheDocument();

    const lenscraft = within(panel).getByText('Lenscraft 18-50mm').closest('li')!;
    await user.click(within(lenscraft).getByRole('button', { name: 'Choose' }));

    expect(await screen.findByText('$1,148.00')).toBeInTheDocument();
    expect(replacedWith).toEqual({ productId: 5 });
    expect(screen.getByText('Chosen by you.')).toBeInTheDocument();
  });

  it('locks an item, re-optimizes with a chosen strategy and explains when nothing fits', async () => {
    const current = { bundle: draft };
    const calls: string[] = [];
    workspaceEndpoints(current);
    server.use(
      http.patch(apiPath('/bundles/draft-1/items/cameras'), async ({ request }) => {
        calls.push(`lock ${JSON.stringify(await request.json())}`);
        current.bundle = { ...current.bundle, items: [{ ...camera, locked: true }, lens] };
        return HttpResponse.json(current.bundle);
      }),
      http.post(apiPath('/bundles/draft-1/reoptimize'), ({ request }) => {
        calls.push(`reoptimize ${new URL(request.url).search}`);
        return HttpResponse.json(
          { status: 409, code: 'REOPTIMIZE_INFEASIBLE', detail: 'Your locked items plus the cheapest compatible choices cost $948.00, which is more than your $500.00 budget.',
            suggestions: ['Raise the budget to at least $948.00.'] },
          { status: 409 },
        );
      }),
    );
    const { user } = renderApp('/bundles/draft-1');

    const cameraRow = (await screen.findByText('Fotonix FX-30')).closest('li')!;
    await user.click(within(cameraRow).getByRole('button', { name: 'Lock' }));
    expect(await within(cameraRow).findByRole('button', { name: 'Unlock' })).toBeInTheDocument();
    expect(screen.getByText(/1 locked item stays/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Optimize for'), 'PREMIUM');
    await user.click(screen.getByRole('button', { name: /re-optimize unlocked items/i }));

    expect(await screen.findByText(/more than your \$500.00 budget/)).toBeInTheDocument();
    expect(screen.getByText('Raise the budget to at least $948.00.')).toBeInTheDocument();
    expect(calls).toEqual(['lock {"locked":true}', 'reoptimize ?strategy=PREMIUM']);
  });

  it('saves the bundle under a name', async () => {
    const current = { bundle: draft };
    let savedAs: unknown;
    workspaceEndpoints(current);
    server.use(
      http.post(apiPath('/bundles/draft-1/save'), async ({ request }) => {
        savedAs = await request.json();
        current.bundle = { ...draft, status: 'SAVED', name: 'Studio v1' };
        return HttpResponse.json(current.bundle);
      }),
    );
    const { user } = renderApp('/bundles/draft-1');

    const name = await screen.findByLabelText('Bundle name');
    await user.clear(name);
    await user.type(name, 'Studio v1');
    await user.click(screen.getByRole('button', { name: /save bundle/i }));

    expect(await screen.findByRole('heading', { name: 'Studio v1' })).toBeInTheDocument();
    expect(screen.getByText('Saved')).toBeInTheDocument();
    expect(savedAs).toEqual({ name: 'Studio v1' });
  });

  it('shows recommended bundles read-only and customizes them into an editable copy', async () => {
    const recommended: Bundle = { ...draft, id: 'rec-1', status: 'RECOMMENDED', editable: false, sourceBundleId: null };
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/bundles/rec-1'), () => HttpResponse.json(recommended)),
      http.post(apiPath('/bundles/rec-1/customize'), () => HttpResponse.json(draft, { status: 201 })),
      http.get(apiPath('/bundles/draft-1'), () => HttpResponse.json(draft)),
      http.get(apiPath('/bundles/draft-1/accessories'), () => HttpResponse.json([])),
    );
    const { user } = renderApp('/bundles/rec-1');

    expect(await screen.findByText(/This is a recommended bundle/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /swap/i })).toBeNull();

    await user.click(screen.getByRole('button', { name: /customize this bundle/i }));

    expect(await screen.findAllByRole('button', { name: /swap/i })).toHaveLength(2);
    expect(location.current.pathname).toBe('/bundles/draft-1');
  });
});
