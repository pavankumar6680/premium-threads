import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { CategoryOption, Goal, GoalAnalysis, TemplateDetail } from './api';

const option = (slug: string, name: string, minPrice: number, brands: string[] = []): CategoryOption => ({
  slug, name, departmentSlug: 'home-office', departmentName: 'Home Office & Furniture',
  inStockCount: 5, minPrice, maxPrice: minPrice * 4, brands,
});

const desks = option('desks', 'Desks', 129, ['Ergolift', 'Northdesk']);
const chairs = option('chairs', 'Chairs', 99, ['Sittwell']);
const monitors = option('monitors', 'Monitors', 129, ['Vantix']);
const webcams = option('webcams', 'Webcams', 39, ['Visio']);

const template: TemplateDetail = {
  slug: 'home-office', title: 'Professional home office', tagline: 'Remote work', description: '',
  defaultBudget: 1200, essentialsMinimum: 357,
  usageOptions: [{ tag: 'ergonomic', label: 'Ergonomic comfort' }, { tag: 'compact', label: 'Small space' }],
  items: [
    { category: desks, required: true, selectedByDefault: true, quantity: 1, note: null },
    { category: chairs, required: true, selectedByDefault: true, quantity: 1, note: 'Lumbar support matters' },
    { category: monitors, required: true, selectedByDefault: true, quantity: 1, note: null },
    { category: webcams, required: false, selectedByDefault: false, quantity: 1, note: null },
  ],
};

function useGoalEndpoints() {
  server.use(
    http.get(apiPath('/goal-categories'), () => HttpResponse.json([desks, chairs, monitors, webcams])),
    http.get(apiPath('/goal-templates/home-office'), () => HttpResponse.json(template)),
  );
}

const kindOf = (category: string) =>
  within(screen.getByRole('radiogroup', { name: `${category}: what do you need?` }));

describe('goal-based shopping', () => {
  it('interprets a described goal and pre-fills the wizard with it', async () => {
    useGoalEndpoints();
    const analysis: GoalAnalysis = {
      source: 'RULES', templateSlug: 'home-office', title: 'Professional home office', budget: 1500, quality: 'BALANCED',
      items: [
        { categorySlug: 'desks', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'chairs', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'monitors', kind: 'OWNED', quantity: 1 },
      ],
      usageTags: ['ergonomic'], preferredBrands: [], matchedTerms: ['home office', 'desk', '$1,500'], clarifications: [],
    };
    let sentText: unknown;
    server.use(
      http.post(apiPath('/goals/analyze'), async ({ request }) => {
        sentText = ((await request.json()) as { text: string }).text;
        return HttpResponse.json(analysis);
      }),
    );
    const { user } = renderApp('/goals');

    await user.type(screen.getByLabelText('Describe your goal'), 'Home office for $1,500, I already have a monitor');
    await user.click(screen.getByRole('button', { name: /plan my bundle/i }));

    expect(await screen.findByRole('heading', { name: 'Build your goal' })).toBeInTheDocument();
    expect(sentText).toBe('Home office for $1,500, I already have a monitor');
    expect(screen.getByText(/pre-filled this from your description/)).toBeInTheDocument();
    expect(screen.getByText('$1,500')).toBeInTheDocument(); // recognised term chip
    expect(kindOf('Desks').getByRole('radio', { name: 'Need it' })).toBeChecked();
    expect(kindOf('Monitors').getByRole('radio', { name: 'Already own' })).toBeChecked();
    // template items the text did not mention stay visible but skipped
    expect(kindOf('Webcams').getByRole('radio', { name: 'Skip' })).toBeChecked();
  });

  it('walks through the wizard and saves exactly the reviewed requirements', async () => {
    useGoalEndpoints();
    let created: Record<string, unknown> | undefined;
    const saved: Goal = {
      id: '6f1c2a54-9b1e-4f3e-8a0c-2d5e7b9c1a11', title: 'Professional home office',
      template: { slug: 'home-office', title: 'Professional home office' }, rawText: null, budget: 1200, quality: 'BALANCED',
      items: [
        { category: desks, kind: 'REQUIRED', quantity: 1 },
        { category: chairs, kind: 'REQUIRED', quantity: 1 },
        { category: monitors, kind: 'OWNED', quantity: 1 },
      ],
      preferredBrands: [], usageTags: ['ergonomic'], space: null, notes: null, createdAt: '2026-10-17T12:00:00Z',
      estimate: { essentialsMinimum: 228, selectedMinimum: 228, essentialsFit: true, selectionFits: true, unavailableCategories: [] },
    };
    server.use(
      http.post(apiPath('/goals'), async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(saved, { status: 201 });
      }),
      http.get(apiPath(`/goals/${saved.id}`), () => HttpResponse.json(saved)),
    );
    const { user } = renderApp('/goals/new?template=home-office');

    await screen.findByRole('radiogroup', { name: 'Monitors: what do you need?' });
    await user.click(kindOf('Monitors').getByRole('radio', { name: 'Already own' }));
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByLabelText('Total budget (USD)')).toHaveValue(1200);
    expect(screen.getByText(/Your budget leaves \$972\.00/)).toBeInTheDocument(); // 1200 - (129 + 99)
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await user.click(await screen.findByRole('checkbox', { name: 'Ergonomic comfort' }));
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByRole('heading', { name: 'Review your goal' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /create my goal/i }));

    expect(await screen.findByRole('heading', { name: 'Professional home office', level: 1 })).toBeInTheDocument();
    expect(location.current.pathname).toBe(`/goals/${saved.id}`);
    expect(screen.getByText('Your budget covers the essentials')).toBeInTheDocument();
    expect(created).toMatchObject({
      templateSlug: 'home-office',
      budget: 1200,
      quality: 'BALANCED',
      usageTags: ['ergonomic'],
      space: null,
      items: [
        { categorySlug: 'desks', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'chairs', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'monitors', kind: 'OWNED', quantity: 1 },
      ], // the skipped webcam is not sent
    });
  });

  it('does not continue until something is selected to buy', async () => {
    useGoalEndpoints();
    const { user } = renderApp('/goals/new');

    await user.click(await screen.findByRole('button', { name: /continue/i }));

    expect(await screen.findByText('Choose at least one item to buy')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Add a category'), 'chairs');
    await user.click(screen.getByRole('button', { name: /continue/i }));
    expect(await screen.findByLabelText('Total budget (USD)')).toBeInTheDocument();
  });
});
