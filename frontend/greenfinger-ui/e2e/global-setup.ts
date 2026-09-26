import type { FullConfig } from '@playwright/test';
import { ADMIN, CORPUS } from './support';

/**
 * The corpus the read-only specs read.
 *
 * Search and the delete plan have nothing to assert against an empty installation, and for a long
 * time these specs simply failed on any machine that did not happen to have the right catalog
 * lying around -- which is why they went a year without being run. So the suite makes its own: a
 * small crawl of books.toscrape with the index, the vectors and the images on, because the three
 * search modes read three different stores and a corpus missing one of them tests two thirds of
 * the page.
 *
 * It is made once and left behind: the crawl is the slow part of this suite, and a second run on
 * the same machine should not pay for it again. `GF_E2E_CATALOG` points at another catalog
 * instead, and then nothing here runs.
 */

/** Small enough to finish in a minute, big enough for a second page of results. */
const CORPUS_DEFINITION = {
  name: CORPUS,
  url: 'https://books.toscrape.com',
  cat: 'other',
  outputTypes: ['FILE', 'INDEX', 'VECTOR'],
  maxFetchSize: 15,
  maxFetchDepth: 2,
  fetchInterval: 200,
  fetchDuration: 5,
};

export default async function seed(config: FullConfig) {
  const baseURL = config.projects[0]?.use?.baseURL;
  if (!baseURL) {
    throw new Error('No baseURL: set GF_E2E_URL in .env to the address the server answers on.');
  }
  if (process.env['GF_E2E_CATALOG']) {
    return;
  }

  const token = await signIn(baseURL);
  const call: Call = async (path: string, init?: RequestInit) => {
    const answer = await fetch(`${baseURL}${path}`, {
      ...init,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
    });
    return answer.json();
  };

  // A version being served is the precondition every read-only spec has: a catalog that exists
  // but has never finished crawling answers "nothing has finished yet", which is not a failure of
  // the page. searchVersion is -1 until one is published.
  const state = await stateOf(call);
  if ((state?.searchVersion ?? -1) >= 0) {
    return;
  }

  console.log(`[e2e] making the corpus '${CORPUS}' -- this crawls, so it takes a minute or two`);
  if (!state) {
    const made = await call('/v2/catalog', {
      method: 'POST',
      body: JSON.stringify(CORPUS_DEFINITION),
    });
    if (!made?.success) {
      throw new Error(`Could not define the corpus: ${made?.message}`);
    }
  }
  const started = await call(`/v2/crawl/${CORPUS}`, { method: 'POST' });
  if (!started?.success) {
    throw new Error(`Could not crawl the corpus: ${started?.message}`);
  }
  await waitForIdle(call);
  console.log(`[e2e] corpus '${CORPUS}' is ready`);
}

async function signIn(baseURL: string): Promise<string> {
  const answer = await fetch(`${baseURL}/v2/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ADMIN),
  });
  const body = await answer.json();
  if (!body?.success) {
    throw new Error(`Cannot sign in at ${baseURL}: ${body?.message ?? answer.status}`);
  }
  return body.data.token;
}

/** What the server says about the corpus, or undefined when it has never heard of it. */
async function stateOf(call: Call): Promise<CrawlState | undefined> {
  const status = await call('/v2/crawl/status');
  return (status?.data ?? []).find((one) => one.name === CORPUS);
}

/**
 * A crawl of a small site ends when the counters have been still for the idle timeout, so this
 * waits for the crawl to stop running rather than for a page count.
 *
 * `/v2/crawl/status` answers for every catalog, running or not -- the list never empties, and a
 * wait written as "until the list is empty" waits for ever.
 */
async function waitForIdle(call: Call) {
  const deadline = Date.now() + 10 * 60_000;
  for (;;) {
    const state = await stateOf(call);
    if (state && !state.running) {
      if (state.searchVersion < 0) {
        throw new Error('The corpus crawl ended without publishing a version to search.');
      }
      return;
    }
    if (Date.now() > deadline) {
      throw new Error('The corpus crawl did not finish within ten minutes.');
    }
    await new Promise((wake) => setTimeout(wake, 5_000));
  }
}

interface CrawlState {
  name: string;
  running: boolean;
  searchVersion: number;
}

type Call = (path: string, init?: RequestInit) => Promise<{ data?: CrawlState[] } & Record<string, unknown>>;
