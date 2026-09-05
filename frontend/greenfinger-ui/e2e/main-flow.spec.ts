import { expect, test } from '@playwright/test';
import { ADMIN, SUPPORT, signIn } from './support';

/**
 * The path an operator actually walks: sign in, define a crawl, run it, watch it.
 *
 * The unit tests know each piece in isolation and would all still pass if the pieces had stopped
 * agreeing with each other -- a renamed field, a route that no longer matches, a token the server
 * stopped accepting. This is the test that fails then.
 *
 * It crawls for real, against a live site, which is why it asks for one page and cleans up after
 * itself. A catalog named for the run means a leftover from a failed run is recognisable.
 */

test.describe('the main flow', () => {
  test('a visitor who is not signed in gets the form and nothing else', async ({ page }) => {
    await page.goto('/catalogs');

    await expect(page).toHaveURL(/\/login/);
    // no shell: offering Catalogs and Search to somebody who cannot open them is a dead end
    await expect(page.getByRole('button', { name: 'Toggle navigation' })).toHaveCount(0);
  });

  test('a wrong password is answered on the form, not by a redirect', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('textbox', { name: 'Username' }).fill('admin');
    await page.getByRole('textbox', { name: 'Password' }).fill('not-the-password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByText(/wrong username or password/i)).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('support sees the data and none of the buttons that change it', async ({ page }) => {
    await signIn(page, SUPPORT);

    await expect(page.getByRole('link', { name: 'New catalog' })).toHaveCount(0);
    await expect(page.getByText(/read only/i)).toBeVisible();
  });

  test('the version badge comes from the server', async ({ page }) => {
    await signIn(page, ADMIN);

    await expect(page.locator('.gf-version')).toHaveText(/\d+\.\d+/);
  });

  test('the theme choice survives a reload', async ({ page }) => {
    await signIn(page, ADMIN);

    // system -> light -> dark
    await page.getByRole('button', { name: /switch to light/i }).click();
    await page.getByRole('button', { name: /switch to dark/i }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });

  test('create a catalog, run it, watch it, remove it', async ({ page }) => {
    const name = `e2e-${Date.now()}`;
    await signIn(page, ADMIN);

    await page.getByRole('link', { name: 'New catalog' }).first().click();
    await page.getByRole('textbox', { name: 'Url', exact: true }).fill('https://books.toscrape.com');
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill(name);

    // one page and one minute: this is a test of the wiring, not of the crawler
    await page.getByRole('button', { name: /how far, how fast/i }).click();
    await page.getByRole('spinbutton', { name: 'Max fetch size' }).fill('1');
    await page.getByRole('spinbutton', { name: /fetch duration/i }).fill('1');

    await page.getByRole('button', { name: 'Create catalog' }).click();
    await expect(page).toHaveURL(/\/catalogs/);
    const tile = page.locator('.gf-tile', { hasText: name });
    await expect(tile).toBeVisible();

    await tile.getByRole('button', { name: 'Crawl' }).click();

    // the monitor is where a running crawl is watched, and it has to show this one
    await tile.getByRole('link', { name: /monitor/i }).click();
    await expect(page).toHaveURL(new RegExp(`/catalogs/${name}/monitor`));
    await expect(page.getByRole('heading', { name })).toBeVisible();
    // "Running" while it is, "Last run" once it is not: either says the run reached the page
    await expect(page.locator('.gf-eyebrow')).toHaveText(/running|last run/i);
    // and the counters the monitor exists to show are on the page
    await expect(page.getByText('Pages saved')).toBeVisible();

    // Clean up the crawled data first, on the Monitor page. Deleting the definition is
    // deliberately only that -- the pages stay in the files, the index and the vector store --
    // so a test that skipped this step left an e2e-* directory behind on every run.
    const zone = page.locator('.gf-danger-zone');
    await zone.getByRole('button', { name: 'all' }).click();
    await zone.getByRole('spinbutton', { name: /keep the newest/i }).fill('0');
    await zone.getByRole('button', { name: /show me what would go/i }).click();
    // the plan is empty when the crawl saved nothing, and there is then nothing to confirm
    const forGood = zone.getByRole('button', { name: /delete for good/i });
    if (await forGood.isVisible().catch(() => false)) {
      await forGood.click();
      await page.getByRole('button', { name: /delete|confirm|yes/i }).last().click();
      await expect(zone.locator('.gf-plan-table')).toHaveCount(0, { timeout: 30_000 });
    }

    // then the definition: a test that leaves catalogs behind makes the next run harder to read
    await page.goto('/catalogs');
    const row = page.locator('.gf-tile', { hasText: name });
    await row.getByRole('button', { name: /more/i }).click();
    await page.getByRole('menuitem', { name: /delete definition/i }).click();
    await page.getByRole('button', { name: /delete|confirm|yes/i }).last().click();
    await expect(page.locator('.gf-tile', { hasText: name })).toHaveCount(0, { timeout: 30_000 });
  });
});
