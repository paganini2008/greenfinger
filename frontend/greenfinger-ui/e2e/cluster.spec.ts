import { expect, test, type Page } from '@playwright/test';
import { ADMIN, CORPUS, signIn } from './support';

/**
 * The System page, and the run reports on the monitor page.
 *
 * Both exist to show things that produce no log line: a node that has stopped sharing work, an
 * inbound buffer that is discarding messages, a crawl that ended with urls outstanding, a setting
 * that is not what the yaml says. A test that only checked they render would miss the point --
 * what is asserted here is that the numbers on them come from the node rather than from a
 * placeholder.
 *
 * The page is three tabs, and each asks a different question, so each test opens its own.
 */

/** The tabs are buttons rather than routes: the url does not change, so the click is the setup. */
async function open(page: Page, tab: 'Health' | 'Cluster' | 'Settings') {
  await page.getByRole('button', { name: tab, exact: true }).click();
}

test.describe('the System page', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto('/cluster');
  });

  test('says which cluster is being asked, and whether anything is wrong', async ({ page }) => {
    // the heading is the cluster's own name, which is how two installations are told apart
    await expect(page.getByRole('heading', { level: 1 })).not.toBeEmpty();
    const badges = page.locator('.gf-hero-badges .gf-chip');
    await expect(badges.filter({ hasText: /UP|DOWN|UNKNOWN/ })).toBeVisible();
    await expect(badges.filter({ hasText: /\d+ members/ })).toBeVisible();
  });

  test('health asks every member in turn, not just the one that answered', async ({ page }) => {
    await open(page, 'Health');
    await expect(page.getByRole('heading', { name: 'Members' })).toBeVisible();

    // a row per member, each with the role it claims for itself
    const rows = page.locator('.gf-plan-table tbody tr');
    await expect(rows.first()).toBeVisible();
    await expect(rows.first()).toContainText(/leader|follower|no answer/);
  });

  test('cluster reports this node: its address, its role, and how long it has been up', async ({
    page,
  }) => {
    await open(page, 'Cluster');

    // one node is a definition list, several are a table: both have to say the same four things
    const single = page.locator('.gf-defs').first();
    if (await single.isVisible().catch(() => false)) {
      await expect(single).toContainText('Address');
      await expect(single).toContainText('Role');
      await expect(single).toContainText('Up for');
    } else {
      const rows = page.locator('.gf-plan-table tbody tr');
      await expect(rows.first()).toBeVisible();
      await expect(rows.first()).toContainText(/leader|follower|no answer/);
    }
  });

  test('traffic lists the channels, or says why there are none yet', async ({ page }) => {
    await open(page, 'Cluster');
    await expect(page.getByRole('heading', { name: 'Traffic' })).toBeVisible();

    // a channel appears once it has carried something, so an idle node has none -- and the page
    // has to say that rather than show an empty table, which reads like a broken cluster
    const table = page.locator('.gf-plan-table').filter({ hasText: 'Channel' });
    if ((await table.count()) === 0) {
      await expect(page.getByText(/Nothing has been sent yet/)).toBeVisible();
    } else {
      await expect(table.first()).toContainText('Sent');
      await expect(table.first().locator('tbody tr').first()).toBeVisible();
    }
  });

  test('a single node says so, because a crawl on it will not be shared', async ({ page }) => {
    const alone = page.getByText(/This node is alone/);
    const members = await page
      .locator('.gf-hero-badges .gf-chip')
      .filter({ hasText: /\d+ members/ })
      .innerText();
    if (members.startsWith('1 ')) {
      await expect(alone).toBeVisible();
    } else {
      await expect(alone).toHaveCount(0);
    }
  });

  test('settings are the merged values, searchable, with the secrets hidden', async ({ page }) => {
    await open(page, 'Settings');
    await expect(page.getByRole('heading', { name: /In force on this node/ })).toBeVisible();

    // every group the server reports is greenfinger's own; nobody else's settings are on this page
    const prefixes = page.locator('.gf-settings-prefix');
    await expect(prefixes.first()).toContainText('greenfinger');

    const find = page.getByRole('searchbox', { name: /narrow the settings/i });
    await find.fill('secret');

    // what is left is only the secrets, and every one of them is masked rather than shown
    const values = page.locator('.gf-settings-value');
    await expect(values.first()).toBeVisible();
    const count = await values.count();
    for (let i = 0; i < count; i++) {
      await expect(values.nth(i)).toHaveText('******');
    }

    // and a setting nobody filled in reads as unset rather than as an empty cell
    await find.fill('weaviate.apiKey');
    await expect(page.locator('.gf-settings-value').first()).toHaveText('not set');
  });
});

test.describe('the run reports', () => {
  test('a finished crawl appears on the monitor page with what it produced', async ({ page }) => {
    await signIn(page, ADMIN);
    await page.goto(`/catalogs/${CORPUS}/monitor`);

    const runs = page.locator('.gf-runs');
    await expect(runs).toBeVisible();
    // a report is written when a crawl finishes, and it is the only place these numbers survive
    await runs.locator('.gf-bar').first().click();
    await expect(runs.getByText('Ended because')).toBeVisible();
    await expect(runs.getByText(/url\(s\) dispatched/)).toBeVisible();

    // dispatched against handled, as a band: the gap is what a run left behind
    await expect(runs.locator('.gf-segbar .gf-seg').first()).toBeVisible();
    await expect(runs.locator('.gf-seg-legend')).toContainText('Handled');
  });
});
