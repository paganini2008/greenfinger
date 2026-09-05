import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  ClusterBuffer,
  ClusterChannel,
  ClusterStatus,
  CrawlStatus,
  ProxyNode,
  HealthComponent,
  HealthReport,
  StorageUsage,
} from '../../core/api.models';
import { Sparkline } from '../../shared/sparkline';

/**
 * A component's report as a flat list of key and value.
 *
 * One level of nesting is unwrapped into `parent.child` keys rather than printed as json: the
 * thread pools report themselves as an object, and a wall of braces in the middle of a row of
 * pills is exactly the kind of thing that makes an operator stop reading the page. Deeper than
 * that is left as json, because nothing here goes deeper and guessing what it would look like is
 * how a renderer acquires cases nobody ever sees.
 */
function flatten(values: Record<string, unknown>, prefix = ''): { key: string; value: string }[] {
  return Object.entries(values).flatMap(([key, value]) => {
    const name = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value) && !prefix) {
      return flatten(value as Record<string, unknown>, name);
    }
    return [{ key: name, value: typeof value === 'object' ? JSON.stringify(value) : String(value) }];
  });
}

/** How many samples the throughput charts keep. At one every three seconds, five minutes of it. */
const HISTORY = 100;

/** How often the catalogs' counters are read. Faster than the cluster poll; see the charts. */
const CRAWL_POLL_MILLIS = 2000;

/**
 * How this node and its stores are doing: the cluster it is in, every message it has carried, the
 * checks behind its health, and how much of the blob store the crawls have taken.
 *
 * This node's view alone, and deliberately so: every node answers only for itself, and a page that
 * presented one node's numbers as the cluster's would hide precisely the node that had stopped
 * working. Which node is being asked is at the top of the page.
 *
 * The numbers worth a page of their own are the ones that produce no log line. A full inbound
 * buffer discards messages silently -- that is the design, because blocking the producer would
 * take the whole dispatch chain down with it -- so a non-zero dropped count is real work lost and
 * nothing else will ever mention it. Likewise a node that can no longer see a leader is running,
 * answering, and doing nothing; and two halves of a split cluster are each perfectly healthy on
 * their own terms.
 */
@Component({
  selector: 'gf-cluster',
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    Sparkline,
  ],
  templateUrl: './cluster.html',
  styleUrl: './cluster.scss',
})
export class ClusterPage {
  private readonly api = inject(ApiService);

  protected readonly status = signal<ClusterStatus | null>(null);
  protected readonly health = signal<HealthReport | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /**
   * Measured on demand, never on the poll.
   *
   * The blob store walk is a directory tree on local disk and a paged list on MinIO, and paying
   * for that every three seconds because a page is open is how a monitoring page becomes the
   * load it was opened to diagnose.
   */
  protected readonly storage = signal<StorageUsage | null>(null);
  protected readonly measuring = signal(false);

  /**
   * Which half of the page is showing.
   *
   * Two halves rather than one long page, because they answer different questions and are read at
   * different moments: `cluster` is "is the machinery working", `crawler` is "is the work getting
   * done". Both poll while they are open and neither polls while it is not.
   */
  protected readonly view = signal<'cluster' | 'crawler'>('cluster');

  /**
   * Which node is being asked, and the ones there are to ask.
   *
   * This page is a node's own account of itself, and the front end spreads requests across every
   * node -- so without pinning, three consecutive polls came from three machines: the counters
   * jumped, the throughput chart was three nodes interleaved, and a warning about one of them
   * appeared and vanished every three seconds. Now the node is chosen and every request on this
   * page goes to it.
   *
   * Empty when the app is not served by its own proxy -- a dev server, or the api on its own
   * domain. Then there is no picker and no pin, and the page behaves as it did before: whichever
   * node answers, answers.
   */
  protected readonly nodes = signal<ProxyNode[]>([]);
  protected readonly node = signal<number | null>(null);

  /** Every catalog and what it has produced, refreshed on the crawl poll. */
  protected readonly crawls = signal<CrawlStatus[]>([]);

  protected readonly healthStatus = computed(() => this.health()?.status ?? '');

  /**
   * Throughput over the life of the page, one entry per poll.
   *
   * The endpoint reports a rate, not a series -- it says what is happening now and has no memory
   * of a minute ago. Keeping the samples here is what turns "4.2 messages a second" into a shape
   * that says whether a crawl is ramping up, holding, or has quietly stopped. It is lost on
   * reload, which is the honest cost of not writing a time series database for it.
   */
  protected readonly tpsHistory = signal<number[]>([]);
  private readonly channelHistory = signal<Record<string, number[]>>({});

  private poll?: Subscription;
  private crawlPoll?: Subscription;

  /** Channels, the application's own first: they are the ones somebody came here about. */
  protected readonly channels = computed<ClusterChannel[]>(() => {
    const all = Object.values(this.status()?.channels ?? {});
    return all.sort((a, b) => {
      if (a.systemChannel !== b.systemChannel) {
        return a.systemChannel ? 1 : -1;
      }
      return a.channel.localeCompare(b.channel);
    });
  });

  /** The buffers, fullest first: an empty queue is never the reason anybody opened this. */
  protected readonly buffers = computed<ClusterBuffer[]>(() =>
    [...(this.status()?.buffers ?? [])].sort((a, b) => b.usage - a.usage || b.dropped - a.dropped),
  );

  protected readonly splitBrain = computed(() => this.status()?.cluster?.splitBrain ?? null);

  /**
   * The replicated stores, one row each, as name and a line of what it reports.
   *
   * Every component invents its own keys -- the cache counts keys and bytes, the record log counts
   * frames -- so they are rendered the way the health checks are, as key=value rather than as
   * columns that would only ever line up by accident.
   */
  protected readonly components = computed(() =>
    Object.entries(this.status()?.components ?? {}).map(([name, values]) => ({
      name,
      entries: flatten(values ?? {}),
    })),
  );

  /** Anything here is work that was lost, or a node that cannot do any. */
  protected readonly warnings = computed<string[]>(() => {
    const status = this.status();
    if (!status) {
      return [];
    }
    const warnings: string[] = [];
    if (status.node.memberCount < 2) {
      warnings.push('This node is alone. A crawl will run, but entirely here.');
    }
    if (status.node.onBreak) {
      warnings.push('This node is resting: still a member, but not taking or sending work.');
    }
    const split = status.cluster?.splitBrain;
    if (split?.splitting) {
      warnings.push(
        `The cluster is split: ${split.holders.length} node(s) each hold the leader port. Rows written on either side may not reach the other.`,
      );
    } else if (split?.everSplit) {
      warnings.push(
        `The cluster has split ${split.occurrences} time(s) since this node started. It is whole now, but rows written during a split may be missing on one side.`,
      );
    }
    for (const buffer of this.buffers()) {
      if (buffer.dropped > 0) {
        warnings.push(
          `The ${buffer.name} buffer has dropped ${buffer.dropped} message(s). Dropping is silent by design, so this is the only place it shows.`,
        );
      }
    }
    for (const channel of this.channels()) {
      if (channel.counters.sendFailures > 0) {
        warnings.push(`${channel.channel}: ${channel.counters.sendFailures} send(s) failed.`);
      }
    }
    return warnings;
  });

  protected readonly uptime = computed(() => {
    const millis = this.status()?.node.uptimeMillis ?? 0;
    const seconds = Math.floor(millis / 1000);
    if (seconds < 60) {
      return `${seconds}s`;
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return `${minutes}m ${seconds % 60}s`;
    }
    return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
  });

  /** Totals across every channel, because "how much has this node carried" has no other answer. */
  protected readonly totals = computed(() => {
    const start = { sent: 0, received: 0, failures: 0, retries: 0, duplicates: 0 };
    return this.channels().reduce(
      (sum, channel) => ({
        sent: sum.sent + channel.counters.sent,
        received: sum.received + channel.counters.received,
        failures:
          sum.failures + channel.counters.sendFailures + channel.counters.receiveFailures,
        retries: sum.retries + channel.counters.retries,
        duplicates: sum.duplicates + channel.counters.duplicates,
      }),
      start,
    );
  });

  /** The busiest channel this node has ever seen, which is what fixes the sparkline's scale. */
  protected readonly peakTps = computed(() =>
    Math.max(1, ...this.channels().map((channel) => channel.throughput.peakTps ?? 0)),
  );

  // ---- the crawler half -----------------------------------------------------------------

  /** Pages and images across every catalog, sampled on each poll, for the two charts. */
  protected readonly pageHistory = signal<number[]>([]);
  protected readonly imageHistory = signal<number[]>([]);

  /** Whatever is running now. Empty is the ordinary state, not an error. */
  protected readonly running = computed(() => this.crawls().filter((crawl) => crawl.running));

  protected readonly crawlTotals = computed(() => {
    const start = { pages: 0, images: 0, urls: 0, handled: 0 };
    return this.crawls().reduce(
      (sum, crawl) => ({
        pages: sum.pages + (crawl.savedResourceCount ?? 0),
        images: sum.images + (crawl.savedImageCount ?? 0),
        urls: sum.urls + (crawl.totalUrlCount ?? 0),
        handled: sum.handled + (crawl.handledUrlCount ?? 0),
      }),
      start,
    );
  });

  /**
   * Pages a second, from the last two samples.
   *
   * The api reports a running total, not a rate, so the rate is the difference over the poll
   * interval. Negative differences are possible and meaningless -- a new run restarts the count
   * at zero -- so they are floored rather than shown as a crawl running backwards.
   */
  protected readonly pageRate = computed(() => this.rateOf(this.pageHistory()));
  protected readonly imageRate = computed(() => this.rateOf(this.imageHistory()));

  private rateOf(series: number[]): number {
    if (series.length < 2) {
      return 0;
    }
    const delta = series[series.length - 1] - series[series.length - 2];
    return Math.max(0, delta / (CRAWL_POLL_MILLIS / 1000));
  }

  /** Per catalog, largest first, with a width for the bar beside it. */
  protected readonly storageBars = computed(() => {
    const rows = this.storage()?.catalogs ?? [];
    const peak = Math.max(1, ...rows.map((row) => row.bytes));
    return [...rows]
      .sort((a, b) => b.bytes - a.bytes)
      .map((row) => ({ ...row, percent: Math.round((row.bytes / peak) * 100) }));
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.poll?.unsubscribe());
    inject(DestroyRef).onDestroy(() => this.crawlPoll?.unsubscribe());
    // the picker's contents, and the first node to ask. A 404 means this app is not behind its
    // own proxy, so there is nobody to name and nothing to pin.
    this.api.proxyNodes().subscribe({
      next: (nodes) => {
        this.nodes.set(nodes);
        if (nodes.length && this.node() === null) {
          this.selectNode(nodes[0].index);
        }
      },
      error: () => this.nodes.set([]),
    });
    queueMicrotask(() => this.start());
  }

  /** Ask a different node: everything on the page is that node's, so all of it is dropped. */
  protected selectNode(index: number): void {
    if (this.node() === index) {
      return;
    }
    this.node.set(index);
    this.tpsHistory.set([]);
    this.channelHistory.set({});
    this.status.set(null);
    this.loading.set(true);
    this.start();
  }

  private start(): void {
    // Both, every time. start() is re-entered whenever a different node is picked, and a second
    // subscription to the crawl poll does not merely double the requests: two samples land per
    // tick, the second one a few milliseconds after the first, so the difference between the last
    // two readings -- which is what the pages-per-second tile is -- came out as zero while the
    // chart beside it climbed.
    this.poll?.unsubscribe();
    this.crawlPoll?.unsubscribe();
    this.api.health(this.node()).subscribe({
      next: (health) => this.health.set(health),
      error: () => this.health.set({ status: 'DOWN', components: [] }),
    });
    this.measure();
    // The catalogs' own counters, on a faster tick than the cluster's: a crawl saving a page a
    // second is the thing the charts are for, and three seconds would draw it as a staircase.
    this.crawlPoll = interval(CRAWL_POLL_MILLIS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.status()),
      )
      .subscribe({
        next: (crawls) => {
          this.crawls.set(crawls);
          const totals = crawls.reduce(
            (sum, crawl) => ({
              pages: sum.pages + (crawl.savedResourceCount ?? 0),
              images: sum.images + (crawl.savedImageCount ?? 0),
            }),
            { pages: 0, images: 0 },
          );
          this.pageHistory.update((series) => [...series, totals.pages].slice(-HISTORY));
          this.imageHistory.update((series) => [...series, totals.images].slice(-HISTORY));
        },
        error: () => undefined,
      });
    // three seconds: throughput is a rate and a rate needs a window, but nobody watches this
    // page for long enough to want it faster
    this.poll = interval(3000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.clusterStatus(this.node())),
      )
      .subscribe({
        next: (status) => {
          this.status.set(status);
          this.remember(status);
          this.error.set(null);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(
            'This node did not answer. It may be starting, or the cluster endpoints may be switched off.',
          );
        },
      });
  }

  /** One sample onto the end of each series, and the oldest off the front. */
  private remember(status: ClusterStatus): void {
    const push = (series: number[], value: number) =>
      [...series, value].slice(-HISTORY);
    this.tpsHistory.update((series) => push(series, status.summary.totalTps ?? 0));
    this.channelHistory.update((all) => {
      const next: Record<string, number[]> = { ...all };
      for (const channel of Object.values(status.channels ?? {})) {
        next[channel.channel] = push(next[channel.channel] ?? [], channel.throughput.tps ?? 0);
      }
      return next;
    });
  }

  protected history(channel: ClusterChannel): number[] {
    return this.channelHistory()[channel.channel] ?? [];
  }

  /** Pressed, or once when the page opens. Never on a timer: see {@link storage}. */
  protected measure(): void {
    this.measuring.set(true);
    this.api.storageUsage().subscribe({
      next: (usage) => {
        this.storage.set(usage);
        this.measuring.set(false);
      },
      error: () => this.measuring.set(false),
    });
  }

  /**
   * A health component's details as one line.
   *
   * Each check invents its own keys -- the database names a product, the disk gives three byte
   * counts -- so there is nothing to lay out in columns and a line of key=value is more honest
   * than a table pretending they share a shape.
   */
  protected detail(component: HealthComponent): string {
    const entries = Object.entries(component.details ?? {});
    if (!entries.length) {
      return '';
    }
    return entries
      .map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : value}`)
      .join('  ·  ');
  }

  /** Bytes as a person would say them, which is the only reason this number is on the page. */
  protected bytes(value: number): string {
    if (value <= 0) {
      return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const power = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)));
    const scaled = value / Math.pow(1024, power);
    return `${scaled.toFixed(power === 0 ? 0 : 1)} ${units[power]}`;
  }

  protected bufferPercent(buffer: ClusterBuffer): number {
    return Math.min(100, Math.round((buffer.usage ?? 0) * 100));
  }

  /** A rate arrives as 0..1 and is read as a percentage; two decimals, because 0.7% matters. */
  protected percent(rate: number | undefined): string {
    return `${((rate ?? 0) * 100).toFixed(2)}%`;
  }
}
