import { test, expect } from '@playwright/test';

test('customer can register, reload a session, save an address, use wishlist/cart and place an order', async ({ page }) => {
  const failures: string[] = [];
  page.on('pageerror', error => failures.push(error.message));
  await page.goto('/auth/register');
  await page.locator('[formControlName="username"]').fill('Frontend Check');
  await page.locator('[formControlName="email"]').fill('frontend-' + Date.now() + '@example.test');
  await page.locator('[formControlName="password"]').fill('frontend-test-password');
  await page.locator('#register-form button[type="submit"]').click();
  await expect(page).toHaveURL('http://localhost:5174/');
  await page.goto('/account/addresses');
  await expect(page.getByRole('heading', { name: 'My address book' })).toBeVisible();
  await page.locator('[formControlName="full_name"]').fill('Frontend Check');
  await page.locator('[formControlName="address_line"]').fill('123 Integration Street');
  await page.locator('[formControlName="city"]').fill('Bangkok');
  await page.getByRole('button', { name: 'Save Address' }).click();
  await expect(page.locator('#primary-address')).toContainText('123 Integration Street');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'My address book' })).toBeVisible();
  await expect(page.locator('#primary-address')).toContainText('123 Integration Street');

  await page.goto('/product/1');
  await expect(page.getByRole('heading', { name: 'The Great Gatsby' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Add to wishlist', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Remove from wishlist', exact: true })).toBeVisible();
  await page.goto('/account/wishlist');
  await expect(page.getByRole('main')).toContainText('The Great Gatsby');
  await page.goto('/product/1');
  const added = page.waitForResponse(response => response.url().endsWith('/api/cart/add') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Add to Cart' }).click();
  expect((await added).status()).toBe(200);
  await page.goto('/cart');
  await expect(page.getByRole('main')).toContainText('The Great Gatsby');
  await page.getByRole('button', { name: 'Proceed to checkout' }).click();
  await expect(page).toHaveURL(/\/checkout$/);
  await expect(page.locator('#checkout-address')).toContainText('123 Integration Street');
  await page.getByRole('button', { name: 'Continue to Payment' }).click();
  await expect(page).toHaveURL(/\/checkout\/payment/);
  await expect(page.getByRole('heading', { name: 'Choose a payment method' })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm Order' }).click();
  await expect(page).toHaveURL(/\/account\/orders$/);
  await expect(page.getByRole('main')).toContainText('The Great Gatsby');
  await expect(page.getByRole('main')).toContainText('Pending');
  await page.goto('/cart');
  await expect(page.getByRole('main')).toContainText('empty');
  expect(failures).toEqual([]);
});

test('public catalog loads all pages and FAQ content through Spring Boot', async ({ page }) => {
  await page.goto('/products');
  const latest = await (await page.request.get('/api/products/60')).json();
  await expect(page.getByText(latest.productName, { exact: true }).first()).toBeVisible();
  await expect(page.locator('mat-paginator')).toContainText('60');
  for (let i = 0; i < 4; i++) await page.getByRole('button', { name: 'Next page' }).click();
  await expect(page.getByText('The Great Gatsby', { exact: true }).first()).toBeVisible();
  await page.goto('/faq');
  await expect(page.getByText('How do I track my order?', { exact: true })).toBeVisible();
  await page.getByText('How do I track my order?', { exact: true }).click();
  await expect(page.getByText(/After placing an order, you'll receive/)).toBeVisible();
});

test('admin login and dashboard work with the admin session', async ({ page }) => {
  await page.goto('/backoffice/login');
  await page.locator('[formControlName="email"]').fill('frontend-admin@example.test');
  await page.locator('[formControlName="password"]').fill('frontend-test-password');
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/backoffice$/);
  await expect(page.getByRole('main')).toContainText('Total Books');
  await expect(page.getByRole('main')).toContainText('60');
  await page.reload();
  await expect(page.getByRole('main')).toContainText('Total Books');
  await page.goto('/backoffice/customers');
  await expect(page.getByRole('heading', { name: 'Customer Management' })).toBeVisible();
});
