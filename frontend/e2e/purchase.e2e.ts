import { expect, test, type Page } from '@playwright/test';

/** The administrator created by the dev profile (see application-dev.yml). */
const ADMIN = { email: process.env.E2E_ADMIN_EMAIL ?? 'admin@buildmybundle.local', password: process.env.E2E_ADMIN_PASSWORD ?? 'Admin12345' };

async function signIn(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: /sign out/i })).toBeVisible();
}

test('a shopper builds an outfit in their size, buys it and the store ships it', async ({ page }) => {
  // 1. A new customer signs up.
  const email = `e2e-${Date.now()}@example.com`;
  await page.goto('/register');
  await page.getByLabel('Full name').fill('Evelyn Endtoend');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Passw0rd!');
  await page.getByLabel('Confirm password').fill('Passw0rd!');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('button', { name: /sign out/i })).toBeVisible();

  // 2. They build a weekend outfit: wardrobe, occasion, their sizes and a favorite color.
  await page.goto('/outfit');
  await page.getByRole('group', { name: 'Wardrobe' }).getByRole('button', { name: 'Men', exact: true }).click();
  await page.getByRole('group', { name: 'Occasion' }).getByRole('button', { name: /weekend casual/i }).click();
  await expect(page.getByLabel('Budget', { exact: true })).toHaveValue('200');
  await page.getByRole('group', { name: 'Tops, dresses & outerwear' }).getByRole('button', { name: 'M', exact: true }).click();
  await page.getByRole('group', { name: 'Waist (jeans, trousers & skirts)' }).getByRole('button', { name: '32', exact: true }).click();
  await page.getByRole('group', { name: 'Colors' }).getByRole('button', { name: 'Navy', exact: true }).click();
  await page.getByRole('button', { name: /style my outfit/i }).click();

  // 3. Three outfits come back with a size and color for every piece; the whole look goes into the cart.
  await expect(page.getByRole('heading', { name: 'Your outfits' })).toBeVisible();
  const balanced = page.getByRole('article', { name: /Balanced bundle/ });
  await expect(balanced).toBeVisible();
  await expect(balanced.getByText(/Size\s*32/).first()).toBeVisible();
  await expect(balanced.getByText(/Colors:/)).toBeVisible();
  await balanced.getByRole('button', { name: /add bundle to cart/i }).click();
  await expect(page.getByText('Bundle added to your cart')).toBeVisible();
  await page.goto('/cart');
  await expect(page.getByText(/32/).first()).toBeVisible();

  // ...and they buy one more piece in their size from a product page.
  await page.goto('/products/levi-s-511-slim-jeans');
  await expect(page.getByRole('button', { name: 'Choose a size' })).toBeDisabled();
  await page.getByRole('button', { name: '32', exact: true }).click();
  await page.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByText("Added Levi's 511 Slim Jeans to your cart")).toBeVisible();

  // 4. Checkout with a new address; the simulated payment is approved.
  await page.goto('/checkout');
  await page.getByLabel('Full name').fill('Evelyn Endtoend');
  await page.getByLabel('Street address').fill('1 Test Street');
  await page.getByLabel('City').fill('Springfield');
  await page.getByLabel('State / region').fill('IL');
  await page.getByLabel('Postal code').fill('62701');
  await page.getByLabel('Country code').fill('US');
  await page.getByRole('button', { name: 'Save address' }).click();
  await page.getByRole('button', { name: /place order/i }).click();
  await expect(page.getByRole('heading', { name: 'Thank you! Your order is confirmed.' })).toBeVisible();
  const orderNumber = (await page.getByRole('heading', { level: 2, name: /^BMB-/ }).textContent())!.trim();
  expect(orderNumber).toMatch(/^BMB-[0-9A-Z]{8}$/);

  // 5. Staff find the order and ship it.
  await page.getByRole('button', { name: /sign out/i }).click();
  await signIn(page, ADMIN.email, ADMIN.password);
  await page.goto(`/admin/orders?orderNumber=${orderNumber}`);
  await page.getByRole('link', { name: orderNumber }).click();
  await expect(page.getByText('Evelyn Endtoend').first()).toBeVisible();
  await page.getByRole('button', { name: 'Mark as shipped' }).click();
  await expect(page.getByText('Order marked as shipped.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Mark as delivered' })).toBeVisible();

  // 6. The change is in the activity log, and the dashboard counts the sale.
  await page.goto('/admin/activity');
  await expect(page.getByRole('cell', { name: /Order shipped/ }).first()).toBeVisible();
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByText('Revenue', { exact: true })).toBeVisible();
});
