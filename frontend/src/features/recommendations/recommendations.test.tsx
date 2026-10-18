import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Goal } from '../goals/api';
import { ada, emptyCart } from '../../test/fixtures';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { Bundle, RecommendationRun, Tier } from './api';

const goal: Goal = {
  id: 'goal-1', title: 'Professional home office', template: { slug: 'home-office', title: 'Professional home office' },
  rawText: null, budget: 1200, quality: 'BALANCED', items: [], preferredBrands: [], usageTags: [], space: null, notes: null,
  createdAt: '2026-10-17T12:00:00Z',
  estimate: { essentialsMinimum: 415, selectedMinimum: 444, essentialsFit: true, selectionFits: true, unavailableCategories: [] },
};

const bundle = (tier: Tier, total: number): Bundle => ({
  id: `bundle-${tier}`, goalId: 'goal-1', goalTitle: 'Professional home office', runId: 'run-1', sourceBundleId: null,
  editable: false, updatedAt: '2026-10-17T12:00:00Z', tier, name: `${tier[0]}${tier.slice(1).toLowerCase()} bundle`,
  status: 'RECOMMENDED', budget: 1200, totalPrice: total, currentTotal: total,
  summary: `Summary for ${tier}.`,
  items: [
    {
      productId: 1, slug: 'desk', sku: 'DSK-001', name: `${tier} desk`, brand: 'Northdesk', categorySlug: 'desks', categoryName: 'Desks',
      quantity: 1, unitPrice: total - 100, currentPrice: total - 100, lineTotal: total - 100, optional: false, locked: false,
      explanation: 'The lowest-priced option in Desks; rated 4.2/5 by 860 customers.', ratingAvg: 4.2, ratingCount: 860,
      availableQuantity: 10, available: true, variant: null, needsSize: false,
    },
    {
      productId: 2, slug: 'arm', sku: 'ARM-002', name: `${tier} arm`, brand: 'Mountix', categorySlug: 'monitor-arms', categoryName: 'Monitor Arms',
      quantity: 1, unitPrice: 100, currentPrice: 100, lineTotal: 100, optional: true, locked: false,
      explanation: 'Works with the monitor (within the arm’s load limit).', ratingAvg: 4.6, ratingCount: 1520,
      availableQuantity: 10, available: true, variant: null, needsSize: false,
    },
  ],
  compatibility: {
    compatible: true,
    passed: [
      { ruleCode: 'MONITOR_ARM_LOAD', label: 'within the arm’s load limit', productIds: [1, 2] },
      { ruleCode: 'MONITOR_ARM_VESA', label: 'matching VESA mount', productIds: [1, 2] },
    ],
    violations: [],
  },
  purchasable: true,
  createdAt: '2026-10-17T12:00:00Z',
});

const run: RecommendationRun = {
  id: 'run-1', goalId: 'goal-1', goalTitle: 'Professional home office', status: 'OK', preferredTier: 'BALANCED', budget: 1200,
  bundles: [bundle('BUDGET', 495), bundle('BALANCED', 812), bundle('PREMIUM', 1196)],
  notes: ['You already own monitors: check that it works with the new monitor arms.'], reasons: [], suggestions: [],
  minimumFeasibleCost: null, stats: { algorithmVersion: 'bnb-1.0', nodesExplored: 1234, durationMs: 18, complete: true },
  createdAt: '2026-10-17T12:00:00Z',
};

describe('recommendations', () => {
  it('generates bundles from a goal and shows all three with explanations', async () => {
    server.use(
      http.get(apiPath('/goals/goal-1'), () => HttpResponse.json(goal)),
      http.post(apiPath('/goals/goal-1/recommendations'), () => HttpResponse.json(run, { status: 201 })),
      http.get(apiPath('/recommendations/run-1'), () => HttpResponse.json(run)),
    );
    const { user } = renderApp('/goals/goal-1');

    await user.click(await screen.findByRole('button', { name: 'Get my bundles' }));

    expect(await screen.findByRole('heading', { name: 'Your outfits' })).toBeInTheDocument();
    expect(location.current.pathname).toBe('/recommendations/run-1');
    const balanced = screen.getByRole('article', { name: /Balanced bundle/ });
    expect(within(balanced).getByText('Your preference')).toBeInTheDocument();
    expect(within(balanced).getByText('$812.00')).toBeInTheDocument();
    expect(within(balanced).getByText('BALANCED desk')).toBeInTheDocument();
    expect(within(balanced).getAllByText(/rated 4.2\/5 by 860 customers/)).toHaveLength(1);
    expect(within(balanced).getByText('2 compatibility checks passed')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(3);
    expect(screen.getByText(/You already own monitors/)).toBeInTheDocument();
    expect(screen.getByText(/evaluated 1,234 combinations in 18 ms/)).toBeInTheDocument();
  });

  it('adds a whole bundle to the cart for signed-in shoppers', async () => {
    let added: string | undefined;
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/recommendations/run-1'), () => HttpResponse.json(run)),
      http.post(apiPath('/cart/bundles/:id'), ({ params }) => {
        added = params.id as string;
        return HttpResponse.json({ ...emptyCart, itemCount: 2 });
      }),
    );
    const { user } = renderApp('/recommendations/run-1');

    const premium = await screen.findByRole('article', { name: /Premium bundle/ });
    await user.click(within(premium).getByRole('button', { name: /add bundle to cart/i }));

    expect(await screen.findByText('Bundle added to your cart')).toBeInTheDocument();
    expect(added).toBe('bundle-PREMIUM');
    expect(screen.getByRole('link', { name: 'Cart, 2 items' })).toBeInTheDocument();
  });

  it('explains why a goal cannot be met and how to fix it', async () => {
    server.use(
      http.get(apiPath('/recommendations/run-2'), () =>
        HttpResponse.json({
          ...run, id: 'run-2', status: 'INFEASIBLE', bundles: [], notes: [], budget: 100, minimumFeasibleCost: 415,
          reasons: ['The cheapest compatible combination of your must-haves costs $415.00, which is more than your $100.00 budget.'],
          suggestions: ['Raise your budget to at least $415.00.', 'Mark Desks as already owned or remove it: the remaining must-haves start at $286.00.'],
        }),
      ),
    );
    renderApp('/recommendations/run-2');

    expect(await screen.findByRole('heading', { name: /couldn’t build a bundle/ })).toBeInTheDocument();
    expect(screen.getByText('$415.00')).toBeInTheDocument();
    expect(screen.getByText('Raise your budget to at least $415.00.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Adjust my goal' })).toHaveAttribute('href', '/goals/new?from=goal-1');
  });
});
