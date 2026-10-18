import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ada, emptyCart, pageOf, card } from '../../test/fixtures';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { CategoryNode, ProductDetail } from './types';

const linenShirt = card();
const oxfordShirt = card({ id: 11, sku: 'UNQ-004', slug: 'uniqlo-oxford-shirt', name: 'Uniqlo Oxford Shirt', brand: 'Uniqlo', price: 179 });

const categories: CategoryNode[] = [
  {
    id: 2, slug: 'men', name: 'Men', description: null, productCount: 2,
    children: [{ id: 7, slug: 'men-jeans', name: 'Jeans', description: null, productCount: 2, children: [] }],
  },
];

const detail: ProductDetail = {
  product: linenShirt,
  description: 'A breathable linen-blend shirt.',
  department: { slug: 'men', name: 'Men' },
  brandInfo: { slug: 'zara', name: 'Zara', description: '' },
  sizeScale: 'ONE_SIZE',
  material: null,
  care: null,
  variants: [{ id: 101, sku: 'ZAR-002', size: 'One size', color: 'Standard', colorHex: null, availableQuantity: 45 }],
  styles: [],
  occasions: [],
  attributes: [{ key: 'inputs', label: 'Inputs', type: 'LIST', unit: null, values: ['HDMI', 'DisplayPort'] }],
  tags: ['office'],
  images: [],
  related: [],
  completeTheLook: [],
};

describe('shop', () => {
  it('lists products for a category and narrows them with a brand filter kept in the URL', async () => {
    const requested: string[] = [];
    server.use(
      http.get(apiPath('/categories'), () => HttpResponse.json(categories)),
      http.get(apiPath('/products/facets'), () =>
        HttpResponse.json({
          brands: [{ value: 'Uniqlo', count: 1 }, { value: 'Zara', count: 1 }],
          minPrice: 179, maxPrice: 249, attributes: [],
        }),
      ),
      http.get(apiPath('/products'), ({ request }) => {
        const url = new URL(request.url);
        requested.push(url.search);
        const brands = url.searchParams.getAll('brand');
        const all = [linenShirt, oxfordShirt].filter((p) => brands.length === 0 || brands.includes(p.brand));
        return HttpResponse.json(pageOf(all));
      }),
    );

    const { user } = renderApp('/shop?category=men-jeans');

    expect(await screen.findByRole('heading', { name: "Men's jeans", level: 1 })).toBeInTheDocument();
    const results = screen.getByRole('region', { name: 'Products' });
    expect(await within(results).findByText('Zara Linen Blend Shirt')).toBeInTheDocument();
    expect(within(results).getByText('Uniqlo Oxford Shirt')).toBeInTheDocument();

    await user.click(screen.getByRole('checkbox', { name: /Uniqlo/ }));

    await expect.poll(() => within(results).queryByText('Zara Linen Blend Shirt')).toBeNull();
    expect(within(results).getByText('Uniqlo Oxford Shirt')).toBeInTheDocument();
    expect(location.current.search).toContain('brand=Uniqlo');
    expect(requested.at(-1)).toContain('category=men-jeans');
    expect(requested.at(-1)).toContain('brand=Uniqlo');
  });

  it('sends anonymous shoppers to sign in when they add to cart, then brings them back', async () => {
    server.use(http.get(apiPath('/products/zara-linen-blend-shirt'), () => HttpResponse.json(detail)));
    const { user } = renderApp('/products/zara-linen-blend-shirt');

    expect(await screen.findByRole('heading', { name: 'Zara Linen Blend Shirt' })).toBeInTheDocument();
    expect(screen.getByText('HDMI, DisplayPort')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /add to cart/i }));

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(location.current.search).toBe('?redirect=%2Fproducts%2Fzara-linen-blend-shirt');
  });

  it('adds the chosen quantity to the cart for signed-in shoppers', async () => {
    let posted: unknown;
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/products/zara-linen-blend-shirt'), () => HttpResponse.json(detail)),
      http.post(apiPath('/cart/items'), async ({ request }) => {
        posted = await request.json();
        return HttpResponse.json({
          ...emptyCart,
          items: [{ productId: 10, sku: 'ZAR-002', slug: linenShirt.slug, name: linenShirt.name, brand: 'Zara', categorySlug: 'women-jackets',
            unitPrice: 249, quantity: 2, lineTotal: 498, availableQuantity: 45, available: true }],
          itemCount: 2, subtotal: 498, tax: 39.84, total: 537.84, checkoutReady: true,
        });
      }),
    );
    const { user } = renderApp('/products/zara-linen-blend-shirt');

    await user.click(await screen.findByRole('button', { name: 'Increase quantity' }));
    await user.click(screen.getByRole('button', { name: /add to cart/i }));

    expect(await screen.findByText('Added Zara Linen Blend Shirt to your cart')).toBeInTheDocument();
    expect(posted).toEqual({ productId: 10, variantId: 101, quantity: 2 });
    expect(screen.getByRole('link', { name: 'Cart, 2 items' })).toBeInTheDocument();
  });
});
