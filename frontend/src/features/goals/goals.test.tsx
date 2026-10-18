import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { CategoryOption, Goal, GoalAnalysis, TemplateDetail } from './api';

const option = (slug: string, name: string, minPrice: number, brands: string[] = []): CategoryOption => ({
  slug, name, departmentSlug: 'office-women', departmentName: 'Women',
  inStockCount: 5, minPrice, maxPrice: minPrice * 4, brands,
});

const trousers = option('women-trousers', 'Trousers', 129, ['Mango', 'Zara']);
const tops = option('women-tops', 'Tops', 99, ['H&M']);
const jackets = option('women-jackets', 'Jackets', 129, ['Zara']);
const handbags = option('handbags', 'Handbags', 39, ['Guess']);

const template: TemplateDetail = {
  slug: 'office-women', title: 'Office ready (women)', tagline: 'Polished workwear', description: '',
  defaultBudget: 1200, essentialsMinimum: 357,
  usageOptions: [{ tag: 'formal', label: 'Formal' }, { tag: 'minimalist', label: 'Minimalist' }],
  items: [
    { category: trousers, required: true, selectedByDefault: true, quantity: 1, note: null },
    { category: tops, required: true, selectedByDefault: true, quantity: 1, note: 'A tailored fit works best' },
    { category: jackets, required: true, selectedByDefault: true, quantity: 1, note: null },
    { category: handbags, required: false, selectedByDefault: false, quantity: 1, note: null },
  ],
};

function useGoalEndpoints() {
  server.use(
    http.get(apiPath('/goal-categories'), () => HttpResponse.json([trousers, tops, jackets, handbags])),
    http.get(apiPath('/goal-templates/office-women'), () => HttpResponse.json(template)),
  );
}

const kindOf = (category: string) =>
  within(screen.getByRole('radiogroup', { name: `${category}: what do you need?` }));

describe('goal-based shopping', () => {
  it('interprets a described goal and pre-fills the wizard with it', async () => {
    useGoalEndpoints();
    const analysis: GoalAnalysis = {
      source: 'RULES', templateSlug: 'office-women', title: 'Office ready (women)', budget: 1500, quality: 'BALANCED',
      items: [
        { categorySlug: 'women-trousers', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'women-tops', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'women-jackets', kind: 'OWNED', quantity: 1 },
      ],
      usageTags: ['formal'], preferredBrands: [], matchedTerms: ['office', 'trousers', '$1,500'], clarifications: [],
    };
    let sentText: unknown;
    server.use(
      http.post(apiPath('/goals/analyze'), async ({ request }) => {
        sentText = ((await request.json()) as { text: string }).text;
        return HttpResponse.json(analysis);
      }),
    );
    const { user } = renderApp('/goals');

    await user.type(screen.getByLabelText('Describe your goal'), 'Office outfit for $1,500, I already have a jacket');
    await user.click(screen.getByRole('button', { name: /plan my outfit/i }));

    expect(await screen.findByRole('heading', { name: 'Build your goal' })).toBeInTheDocument();
    expect(sentText).toBe('Office outfit for $1,500, I already have a jacket');
    expect(screen.getByText(/pre-filled this from your description/)).toBeInTheDocument();
    expect(screen.getByText('$1,500')).toBeInTheDocument(); // recognised term chip
    expect(kindOf('Trousers').getByRole('radio', { name: 'Need it' })).toBeChecked();
    expect(kindOf('Jackets').getByRole('radio', { name: 'Already own' })).toBeChecked();
    // template items the text did not mention stay visible but skipped
    expect(kindOf('Handbags').getByRole('radio', { name: 'Skip' })).toBeChecked();
  });

  it('walks through the wizard and saves exactly the reviewed requirements', async () => {
    useGoalEndpoints();
    let created: Record<string, unknown> | undefined;
    const saved: Goal = {
      id: '6f1c2a54-9b1e-4f3e-8a0c-2d5e7b9c1a11', title: 'Office ready (women)',
      template: { slug: 'office-women', title: 'Office ready (women)' }, rawText: null, budget: 1200, quality: 'BALANCED',
      items: [
        { category: trousers, kind: 'REQUIRED', quantity: 1 },
        { category: tops, kind: 'REQUIRED', quantity: 1 },
        { category: jackets, kind: 'OWNED', quantity: 1 },
      ],
      preferredBrands: [], usageTags: ['formal'], space: null, notes: null, createdAt: '2026-10-17T12:00:00Z',
      estimate: { essentialsMinimum: 228, selectedMinimum: 228, essentialsFit: true, selectionFits: true, unavailableCategories: [] },
    };
    server.use(
      http.post(apiPath('/goals'), async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(saved, { status: 201 });
      }),
      http.get(apiPath(`/goals/${saved.id}`), () => HttpResponse.json(saved)),
    );
    const { user } = renderApp('/goals/new?template=office-women');

    await screen.findByRole('radiogroup', { name: 'Jackets: what do you need?' });
    await user.click(kindOf('Jackets').getByRole('radio', { name: 'Already own' }));
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByLabelText('Total budget (USD)')).toHaveValue(1200);
    expect(screen.getByText(/Your budget leaves \$972\.00/)).toBeInTheDocument(); // 1200 - (129 + 99)
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await user.click(await screen.findByRole('checkbox', { name: 'Formal' }));
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByRole('heading', { name: 'Review your goal' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /create my goal/i }));

    expect(await screen.findByRole('heading', { name: 'Office ready (women)', level: 1 })).toBeInTheDocument();
    expect(location.current.pathname).toBe(`/goals/${saved.id}`);
    expect(screen.getByText('Your budget covers the essentials')).toBeInTheDocument();
    expect(created).toMatchObject({
      templateSlug: 'office-women',
      budget: 1200,
      quality: 'BALANCED',
      usageTags: ['formal'],
      space: null,
      items: [
        { categorySlug: 'women-trousers', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'women-tops', kind: 'REQUIRED', quantity: 1 },
        { categorySlug: 'women-jackets', kind: 'OWNED', quantity: 1 },
      ], // the skipped handbag is not sent
    });
  });

  it('does not continue until something is selected to buy', async () => {
    useGoalEndpoints();
    const { user } = renderApp('/goals/new');

    await user.click(await screen.findByRole('button', { name: /continue/i }));

    expect(await screen.findByText('Choose at least one item to buy')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Add a category'), 'women-tops');
    await user.click(screen.getByRole('button', { name: /continue/i }));
    expect(await screen.findByLabelText('Total budget (USD)')).toBeInTheDocument();
  });
});
