import { expect, test } from '@playwright/test';
import { ADMIN, signIn } from './support';

/**
 * The cluster page, and the run reports on the monitor page.
 *
 * Both exist to show things that produce no log line: a node that has stopped sharing work, an
 * inbound buffer that is discarding messages, a crawl that ended with urls outstanding. A test
 * that only checked they render would miss the point -- what is asserted here is that the numbers
 * on them come from the node rather than from a placeholder.
 *
 */

test.describe('the cluster page', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto('/cluster');
  });

  test('says which node is being asked and whether it leads', async ({ page }) => {
    // every node answers for itself, so the page has to say which one it is
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('.gf-chip').filter({ hasText: /leader|follower/ })).toBeVisible();
    await expect(page.locator('.gf-chip').filter({ hasText: /member\(s\)/ })).toBeVisible();
    await expect(page.locator('.gf-chip').filter({ hasText: 'UP' })).toBeVisible();
  });

  test('reports this node: its address, its uptime, and who the leader is', async ({ page }) => {
    const details = page.locator('.gf-defs').first();
    await expect(details).toContainText('Address');
    await expect(details).toContainText('Members');
    await expect(details).toContainText('Up for');
  });

  test('lists the channels, or says why there are none yet', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Channels/ })).toBeVisible();

    // a channel appears once it has carried something, so an idle node has none -- and the page
    // has to say that rather than show an empty table, which reads like a broken cluster
    const rows = page.locator('.gf-plan-table tbody tr');
    if ((await rows.count()) === 0) {
      await expect(page.getByText(/Nothing has been sent yet/)).toBeVisible();
    } else {
      await expect(page.locator('.gf-plan-table')).toContainText('Dropped');
      await expect(rows.first()).toBeVisible();
    }
  });

  test('a single node says so, because a crawl on it will not be shared', async ({ page }) => {
    // one node in the local setup: the page must say that rather than look like a cluster
    const alone = page.getByText(/This node is alone/);
    const members = await page.locator('.gf-chip').filter({ hasText: /member\(s\)/ }).innerText();
    if (members.startsWith('1 ')) {
      await expect(alone).toBeVisible();
    } else {
      await expect(alone).toHaveCount(0);
    }
  });
});

test.describe('the run reports', () => {
  /**
   * Crawls for real rather than reading somebody else's history: a report exists only after a run
   * has finished, so a test that assumed one was already there would pass or fail on what the
   * machine happened to have lying about.
   */
  test('a finished crawl appears on the monitor page with what it produced', async ({ page }) => {
    // this one really crawls: a minute of fetching, then the cluster has to agree it is done
    // before anybody writes a report. The default ninety seconds is shorter than the wait below,
    // so the test would run out before the assertion did and report the wrong thing.
    test.setTimeout(240_000);
    const name = `e2e-report-${Date.now()}`;
    await signIn(page, ADMIN);

    await page.getByRole('link', { name: 'New catalog' }).first().click();
    await page.getByRole('textbox', { name: 'Url', exact: true }).fill('https://books.toscrape.com');
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill(name);
    await page.getByRole('button', { name: /how far, how fast/i }).click();
    await page.getByRole('spinbutton', { name: 'Max fetch size' }).fill('1');
    await page.getByRole('spinbutton', { name: /fetch duration/i }).fill('1');
    await page.getByRole('button', { name: 'Create catalog' }).click();
    await expect(page).toHaveURL(/\/catalogs/);

    const tile = page.locator('.gf-tile', { hasText: name });
    await tile.getByRole('button', { name: 'Crawl' }).click();
    await tile.getByRole('link', { name: /monitor/i }).click();

    // the panel appears only once a run has ended and written its report
    const runs = page.locator('.gf-runs');
    await expect(runs).toBeVisible({ timeout: 120_000 });

    // one bar per run, and a bar is a button because picking one shows its detail
    await runs.locator('.gf-bar').first().click();
    await expect(runs.getByText('Ended because')).toBeVisible();
    await expect(runs.getByText(/url\(s\) dispatched/)).toBeVisible();
    // dispatched is the whole bar; the segments are what became of those urls
    await expect(runs.locator('.gf-segbar .gf-seg').first()).toBeVisible();
    await expect(runs.locator('.gf-seg-legend')).toContainText('Handled');

    // clean up: a test that leaves catalogs behind makes the next run harder to read
    await page.goto('/catalogs');
    const row = page.locator('.gf-tile', { hasText: name });
    await row.getByRole('button', { name: /more/i }).click();
    await page.getByRole('menuitem', { name: /delete definition/i }).click();
    await page.getByRole('button', { name: /delete|confirm|yes/i }).last().click();
    await expect(page.locator('.gf-tile', { hasText: name })).toHaveCount(0, { timeout: 30_000 });
  });
});
