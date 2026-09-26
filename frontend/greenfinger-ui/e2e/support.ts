import { expect, type Page } from '@playwright/test';

/**
 * What every spec here needs before it can start: an account, and a way in.
 *
 * The two accounts are the two roles the front end distinguishes. Signing in through the form
 * rather than by planting a token is deliberate -- the form is part of what is under test, and a
 * token planted from here would keep passing after the real one stopped being accepted.
 */

export const ADMIN = { username: 'admin', password: 'admin123' };

/**
 * The catalog the read-only specs look at: one crawled with index, vectors and images on.
 *
 * `globalSetup` makes it if it is not there, so a fresh machine can run these. Point
 * `GF_E2E_CATALOG` at another one to read a real installation's data instead.
 */
export const CORPUS = process.env['GF_E2E_CATALOG'] ?? 'e2e-corpus';
export const SUPPORT = { username: 'tester', password: 'tester123' };

export async function signIn(page: Page, who: { username: string; password: string }) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(who.username);
  await page.getByRole('textbox', { name: 'Password' }).fill(who.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/catalogs/);
}
