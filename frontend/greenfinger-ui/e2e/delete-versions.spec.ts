import { expect, test } from '@playwright/test';
import { ADMIN, SUPPORT, signIn } from './support';

/**
 * The dry run in front of the one operation that cannot be undone.
 *
 * Nothing here deletes anything: that is the point. The report has to be produced without
 * touching a byte, and the data has to still be there afterwards -- a dry run that quietly
 * deleted would look exactly like a working one until the day somebody read the report and
 * decided not to go ahead.
 *
 * Precondition: a catalog with at least one crawled version -- `books2` in the local setup.
 */

const CATALOG = process.env['GF_E2E_CATALOG'] ?? 'books2';

test.describe('removing old versions', () => {
  test('the plan says what would go, and nothing goes', async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto(`/catalogs/${CATALOG}/monitor`);
    await expect(page.getByRole('heading', { name: CATALOG })).toBeVisible();

    const pagesSaved = page.getByText('Pages saved');
    await expect(pagesSaved,
      `no catalog named '${CATALOG}' with a crawled version; set GF_E2E_CATALOG to one that exists`)
      .toBeVisible();

    const zone = page.locator('.gf-danger-zone');
    await zone.getByRole('button', { name: 'all' }).click();
    // keep nothing, so every version that exists shows up in the report
    await zone.getByRole('spinbutton', { name: /keep the newest/i }).fill('0');
    await zone.getByRole('button', { name: /show me what would go/i }).click();

    const rows = zone.locator('.gf-plan-table tbody tr');
    await expect(rows.first()).toBeVisible();
    // a row per version per layer, each with something to remove
    await expect(rows.first().locator('td').nth(0)).toHaveText(/^v\d+$/);
    // the report names the layer as the server does, which is the enum: DB, FILE, INDEX, VECTOR
    await expect(rows.first().locator('td').nth(1)).toHaveText(/db|file|index|vector/i);

    // the confirmation is a second step, and it has not been taken
    await expect(zone.getByRole('button', { name: /delete for good/i })).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    // and the data is untouched: the report was a question, not an instruction
    await page.reload();
    await expect(page.getByText('Pages saved')).toBeVisible();
    await page.goto('/search');
    await page.getByPlaceholder('anything you crawled').fill('books');
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await expect(page.locator('.gf-result').first()).toBeVisible();
  });

  test('keeping more versions than exist reports nothing to remove', async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto(`/catalogs/${CATALOG}/monitor`);

    const zone = page.locator('.gf-danger-zone');
    await zone.getByRole('spinbutton', { name: /keep the newest/i }).fill('99');
    await zone.getByRole('button', { name: /show me what would go/i }).click();

    await expect(page.getByText(/nothing to remove/i)).toBeVisible();
    await expect(zone.locator('.gf-plan-table')).toHaveCount(0);
  });

  test('support never sees the danger zone at all', async ({ page }) => {
    await signIn(page, SUPPORT);
    await page.goto(`/catalogs/${CATALOG}/monitor`);
    await expect(page.getByText('Pages saved')).toBeVisible();

    // hidden, not merely disabled: a button that cannot work is a worse answer than no button
    await expect(page.locator('.gf-danger-zone')).toHaveCount(0);
  });
});
