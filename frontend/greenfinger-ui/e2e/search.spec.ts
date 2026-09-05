import { expect, test } from '@playwright/test';
import { ADMIN, signIn } from './support';

/**
 * The three search modes, each against the store it actually uses.
 *
 * They look like one box with a switch, which is exactly why they are worth an end to end test:
 * three different backends (Elasticsearch, the text vectors, the image vectors) reached through
 * one component, and nothing in the unit tests notices when one of the three stops answering.
 *
 * Precondition: the server has a catalog crawled with index and vector output on -- `books2` in
 * the local setup. Without it there is nothing to find and every assertion here is meaningless,
 * so the first test says so plainly rather than passing on an empty store.
 */

/** books.toscrape has this on every page; any crawl of it answers, with more than one page of them. */
const COMMON_WORD = 'books';

test.describe('the three ways to search', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto('/search');
  });

  test('words go to elasticsearch and come back with a total and highlights', async ({ page }) => {
    await page.getByPlaceholder('anything you crawled').fill(COMMON_WORD);
    await page.getByRole('button', { name: 'Search', exact: true }).click();

    const meta = page.locator('.gf-result-meta');
    await expect(meta,
      'nothing indexed: crawl a catalog with --output-types file,index,vector before running these')
      .toContainText(/match\(es\)/);
    await expect(meta).toContainText(/\d+ ms/);
    await expect(page.locator('.gf-result').first()).toBeVisible();
    // the highlight is the thing Elasticsearch does that the vector stores cannot
    await expect(page.locator('.gf-result-snippet').first()).toBeVisible();
  });

  test('words page forward and back on a cursor, not a page number', async ({ page }) => {
    await page.getByPlaceholder('anything you crawled').fill(COMMON_WORD);
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await expect(page.locator('.gf-result').first()).toBeVisible();

    const firstTitle = await page.locator('.gf-result-title').first().innerText();
    await page.getByRole('button', { name: 'Next' }).click();

    await expect(page.locator('.gf-result-meta')).toContainText('page 2');
    await expect(page.locator('.gf-result-title').first()).not.toHaveText(firstTitle);

    await page.getByRole('button', { name: 'Previous' }).click();
    await expect(page.locator('.gf-result-title').first()).toHaveText(firstTitle);
  });

  test('meaning goes to the text vectors and scores every hit', async ({ page }) => {
    await page.getByRole('button', { name: /meaning/i }).click();
    await page.getByPlaceholder('anything you crawled').fill('a story about growing up');
    await page.getByRole('button', { name: 'Search', exact: true }).click();

    // the first call loads the embedding model, which is slower than anything else on the page
    await expect(page.locator('.gf-result').first()).toBeVisible({ timeout: 60_000 });
    // similarity, not a match count: a vector search answers "the nearest n" and has no total
    await expect(page.locator('.gf-chip-vector').first()).toContainText(/similarity 0\.\d{3}/);
    await expect(page.locator('.gf-result-meta')).toHaveCount(0);
  });

  test('pictures are found by description and keep the url they came from', async ({ page }) => {
    await page.getByRole('button', { name: /pictures/i }).click();
    await expect(page.getByPlaceholder('a red book cover on a wooden table')).toBeVisible();
    await page.getByPlaceholder('a red book cover on a wooden table').fill('a green book cover');
    await page.getByRole('button', { name: 'Search', exact: true }).click();

    const shots = page.locator('.gf-shot');
    await expect(shots.first()).toBeVisible({ timeout: 60_000 });

    // the tile draws the archived copy, so it still works after the site moves on. It arrives as
    // an object url because the endpoint that serves it wants the token an <img> cannot send
    await expect(shots.first().locator('img')).toHaveAttribute('src', /^blob:/);
    // ... and keeps the original url beside it, which is the only way back to where it lives
    const origin = shots.first().getByRole('link', { name: 'see it in place' });
    await expect(origin).toHaveAttribute('href', /^https?:\/\//);
  });

  test('pictures page by offset into one ranking', async ({ page }) => {
    await page.getByRole('button', { name: /pictures/i }).click();
    await page.getByPlaceholder('a red book cover on a wooden table').fill('a book cover');
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await expect(page.locator('.gf-shot').first()).toBeVisible({ timeout: 60_000 });

    const firstImage = await page.locator('.gf-shot img').first().getAttribute('src');
    await page.getByRole('button', { name: 'Next' }).click();

    await expect(page.getByText('page 2')).toBeVisible();
    // a different picture, so the offset reached the store rather than being re-sliced locally
    await expect(page.locator('.gf-shot img').first()).not.toHaveAttribute('src', firstImage ?? '');
  });

  test('switching modes clears what the other mode found', async ({ page }) => {
    await page.getByPlaceholder('anything you crawled').fill(COMMON_WORD);
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await expect(page.locator('.gf-result-meta')).toBeVisible();

    await page.getByRole('button', { name: /pictures/i }).click();

    // the Elasticsearch result block belongs to Words alone; leaving it up would be a lie
    await expect(page.locator('.gf-result-meta')).toHaveCount(0);
  });
});
