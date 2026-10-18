import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { CategoryOption, CreateGoalInput, GoalAnalysis, TemplateDetail, TemplateSummary } from '../goals/api';

const option = (slug: string, name: string, minPrice: number): CategoryOption => ({
  slug, name, departmentSlug: 'men', departmentName: 'Men', inStockCount: 6, minPrice, maxPrice: minPrice * 3, brands: [],
});

const summary = (slug: string, title: string): TemplateSummary => ({
  slug, title, tagline: '', defaultBudget: 200, essentialsMinimum: 70, categoryNames: [],
});

const weekendMen: TemplateDetail = {
  slug: 'weekend-casual-men', title: 'Weekend casual (men)', tagline: '', description: '', defaultBudget: 200, essentialsMinimum: 70,
  usageOptions: [{ tag: 'casual', label: 'Casual' }, { tag: 'streetwear', label: 'Streetwear' }],
  items: [
    { category: option('men-tshirts', 'T-Shirts', 25), required: true, selectedByDefault: true, quantity: 1, note: null },
    { category: option('men-jeans', 'Jeans', 45), required: true, selectedByDefault: true, quantity: 1, note: null },
    { category: option('sunglasses', 'Sunglasses', 30), required: false, selectedByDefault: true, quantity: 1, note: null },
    { category: option('watches', 'Watches', 90), required: false, selectedByDefault: false, quantity: 1, note: null },
  ],
};

function useOutfitEndpoints(onCreate: (input: CreateGoalInput) => void) {
  server.use(
    http.get(apiPath('/goal-templates'), () =>
      HttpResponse.json([summary('weekend-casual-men', 'Weekend casual (men)'), summary('weekend-casual-women', 'Weekend casual (women)'),
        summary('office-men', 'Office ready (men)')]),
    ),
    http.get(apiPath('/goal-templates/weekend-casual-men'), () => HttpResponse.json(weekendMen)),
    http.post(apiPath('/goals'), async ({ request }) => {
      onCreate((await request.json()) as CreateGoalInput);
      return HttpResponse.json({ id: 'goal-9' });
    }),
    http.post(apiPath('/goals/goal-9/recommendations'), () => HttpResponse.json({ id: 'run-9' })),
    http.get(apiPath('/recommendations/run-9'), () => new HttpResponse(null, { status: 404 })),
  );
}

describe('build your outfit', () => {
  it('turns occasion, sizes, colors and pieces into a goal and shows the recommended outfits', async () => {
    let created: CreateGoalInput | undefined;
    useOutfitEndpoints((input) => (created = input));
    const { user } = renderApp('/outfit');

    await user.click(await screen.findByRole('button', { name: 'Men' }));
    const occasions = within(screen.getByRole('group', { name: 'Occasion' }));
    expect(occasions.queryByRole('button', { name: /weekend casual.*women/i })).not.toBeInTheDocument();
    await user.click(occasions.getByRole('button', { name: /weekend casual/i }));

    // the occasion fills the budget and the pieces; the optional watch starts skipped
    expect(await screen.findByRole('group', { name: 'Watches' })).toBeInTheDocument();
    expect(screen.getByLabelText('Budget')).toHaveValue(200);
    await user.click(within(screen.getByRole('group', { name: 'Watches' })).getByRole('button', { name: 'Nice to have' }));
    await user.click(within(screen.getByRole('group', { name: 'Sunglasses' })).getByRole('button', { name: 'Skip' }));

    await user.click(within(screen.getByRole('group', { name: 'Tops, dresses & outerwear' })).getByRole('button', { name: 'M' }));
    await user.click(within(screen.getByRole('group', { name: 'Waist (jeans, trousers & skirts)' })).getByRole('button', { name: '32' }));
    await user.click(screen.getByRole('button', { name: 'Navy' }));
    await user.click(screen.getByRole('button', { name: 'Streetwear' }));
    await user.click(screen.getByRole('button', { name: /style my outfit/i }));

    await expect.poll(() => location.current.pathname).toBe('/recommendations/run-9');
    expect(created).toMatchObject({
      templateSlug: 'weekend-casual-men',
      budget: 200,
      usageTags: ['streetwear'],
      fit: { gender: 'MEN', sizeAlpha: 'M', sizeWaist: '32', colors: ['Navy'] },
    });
    expect(created?.items.map((i) => [i.categorySlug, i.kind])).toEqual([
      ['men-tshirts', 'REQUIRED'], ['men-jeans', 'REQUIRED'], ['watches', 'OPTIONAL'],
    ]);
  });

  it('fills the choices from a sentence', async () => {
    useOutfitEndpoints(() => {});
    const analysis: GoalAnalysis = {
      source: 'RULES', templateSlug: 'weekend-casual-men', title: 'Weekend casual (men)', budget: 150, quality: 'BALANCED',
      items: [{ categorySlug: 'watches', kind: 'REQUIRED', quantity: 1 }], usageTags: ['casual'], preferredBrands: [],
      matchedTerms: [], clarifications: [], fit: { gender: 'MEN', sizeAlpha: 'L', sizeWaist: null, colors: ['Black'] },
    };
    server.use(http.post(apiPath('/goals/analyze'), () => HttpResponse.json(analysis)));
    const { user } = renderApp('/outfit');

    await user.type(screen.getByLabelText(/describe it in your own words/i), 'weekend look for him under $150, size L, black');
    await user.click(screen.getByRole('button', { name: /fill in the choices/i }));

    expect(await screen.findByRole('button', { name: 'Men' })).toHaveAttribute('aria-pressed', 'true');
    expect(await screen.findByRole('group', { name: 'Watches' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Watches' })).getByRole('button', { name: 'Need it' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('Budget')).toHaveValue(150);
    expect(within(screen.getByRole('group', { name: 'Tops, dresses & outerwear' })).getByRole('button', { name: 'L' }))
      .toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Black' })).toHaveAttribute('aria-pressed', 'true');
  });
});
