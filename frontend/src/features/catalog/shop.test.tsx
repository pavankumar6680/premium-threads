import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ada, emptyCart, pageOf, product, card } from '../../test/fixtures';
import { location, renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { CategoryNode, ProductDetail } from './types';

const vantix = product();
const pixelon = product({ id: 11, sku: 'MON-004', slug: 'pixelon-24', name: 'Pixelon 24 Gaming Monitor', brand: 'Pixelon', price: 179 });

const categories: CategoryNode[] = [
  {
    id: 2, slug: 'computers', name: 'Computers & Accessories', description: null, productCount: 2,
    children: [{ id: 7, slug: 'monitors', name: 'Monitors', description: null, productCount: 2, children: [] }],
  },
];

const detail: ProductDetail = {
  product: card(vantix),
  description: 'A sharp 27-inch monitor.',
  department: { slug: 'computers', name: 'Computers & Accessories' },
  brandInfo: { slug: 'vantix', name: 'Vantix', description: '' },
  sizeScale: 'ONE_SIZE',
  material: null,
  care: null,
  variants: [{ id: 101, sku: 'MON-002', size: 'One size', color: 'Standard', colorHex: null, availableQuantity: 45 }],
  styles: [],
  occasions: [],
  attributes: [{ key: 'inputs', label: 'Inputs', type: 'LIST', unit: null, values: ['HDMI', 'DisplayPort'] }],
  tags: ['home-office'],
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
          brands: [{ value: 'Pixelon', count: 1 }, { value: 'Vantix', count: 1 }],
          minPrice: 179, maxPrice: 249, attributes: [],
        }),
      ),
      http.get(apiPath('/products'), ({ request }) => {
        const url = new URL(request.url);
        requested.push(url.search);
        const brands = url.searchParams.getAll('brand');
        const all = [vantix, pixelon].filter((p) => brands.length === 0 || brands.includes(p.brand));
        return HttpResponse.json(pageOf(all));
      }),
    );

    const { user } = renderApp('/shop?category=monitors');

    expect(await screen.findByRole('heading', { name: 'Monitors', level: 1 })).toBeInTheDocument();
    const results = screen.getByRole('region', { name: 'Products' });
    expect(await within(results).findByText('Vantix 27 QHD Monitor')).toBeInTheDocument();
    expect(within(results).getByText('Pixelon 24 Gaming Monitor')).toBeInTheDocument();

    await user.click(screen.getByRole('checkbox', { name: /Pixelon/ }));

    await expect.poll(() => within(results).queryByText('Vantix 27 QHD Monitor')).toBeNull();
    expect(within(results).getByText('Pixelon 24 Gaming Monitor')).toBeInTheDocument();
    expect(location.current.search).toContain('brand=Pixelon');
    expect(requested.at(-1)).toContain('category=monitors');
    expect(requested.at(-1)).toContain('brand=Pixelon');
  });

  it('sends anonymous shoppers to sign in when they add to cart, then brings them back', async () => {
    server.use(http.get(apiPath('/products/vantix-27-qhd-monitor'), () => HttpResponse.json(detail)));
    const { user } = renderApp('/products/vantix-27-qhd-monitor');

    expect(await screen.findByRole('heading', { name: 'Vantix 27 QHD Monitor' })).toBeInTheDocument();
    expect(screen.getByText('HDMI, DisplayPort')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /add to cart/i }));

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(location.current.search).toBe('?redirect=%2Fproducts%2Fvantix-27-qhd-monitor');
  });

  it('adds the chosen quantity to the cart for signed-in shoppers', async () => {
    let posted: unknown;
    server.use(
      http.get(apiPath('/auth/me'), () => HttpResponse.json(ada)),
      http.get(apiPath('/products/vantix-27-qhd-monitor'), () => HttpResponse.json(detail)),
      http.post(apiPath('/cart/items'), async ({ request }) => {
        posted = await request.json();
        return HttpResponse.json({
          ...emptyCart,
          items: [{ productId: 10, sku: 'MON-002', slug: vantix.slug, name: vantix.name, brand: 'Vantix', categorySlug: 'monitors',
            unitPrice: 249, quantity: 2, lineTotal: 498, availableQuantity: 45, available: true }],
          itemCount: 2, subtotal: 498, tax: 39.84, total: 537.84, checkoutReady: true,
        });
      }),
    );
    const { user } = renderApp('/products/vantix-27-qhd-monitor');

    await user.click(await screen.findByRole('button', { name: 'Increase quantity' }));
    await user.click(screen.getByRole('button', { name: /add to cart/i }));

    expect(await screen.findByText('Added Vantix 27 QHD Monitor to your cart')).toBeInTheDocument();
    expect(posted).toEqual({ productId: 10, variantId: 101, quantity: 2 });
    expect(screen.getByRole('link', { name: 'Cart, 2 items' })).toBeInTheDocument();
  });
});
