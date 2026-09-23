import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription, catchError, forkJoin, interval, map, of, startWith, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  ClusterBuffer,
  ClusterChannel,
  ClusterStatus,
  ProxyNode,
  HealthComponent,
  HealthReport,
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

/** One member of the cluster, as that member describes itself. */
interface ClusterMember {
  index: number;
  address: string;
  reachable: boolean;
  leader: boolean;
  nodeId: string;
  onBreak: boolean;
  memberCount: number;
  uptime: string;
  tps: number;
  failures: number;
  health: string;
  /** Heap, because "is it about to fall over" is the second question after "is it up". */
  heapUsed: number;
  heapMax: number;
  cpu: number;
}

/**
 * One health-check value, as a phrase rather than as json.
 *
 * The spreader check reports its whole membership under `otherMembers`, and printing that with
 * JSON.stringify put two hundred characters of braces and quotes in the middle of a list somebody
 * reads to find out whether anything is wrong. It is also the same membership the table at the top
 * of this page already lays out properly, so the check only needs to say how many.
 */
function describeValue(value: unknown): string {
  if (Array.isArray(value)) {
    return `${value.length}`;
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>);
    // small flat objects are worth spelling out; anything bigger is a count
    return entries.length <= 3 && entries.every(([, one]) => typeof one !== 'object')
      ? entries.map(([key, one]) => `${key} ${one}`).join(', ')
      : `${entries.length} entries`;
  }
  return String(value);
}

/** Milliseconds of uptime as a person would say them. Used per member and for the asked node. */
function uptimeOf(millis: number): string {
  const seconds = Math.floor((millis ?? 0) / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  return hours < 24 ? `${hours}h ${minutes % 60}m` : `${Math.floor(hours / 24)}d ${hours % 24}h`;
}

/** How many samples the throughput charts keep. At one every three seconds, five minutes of it. */
const HISTORY = 100;

/**
 * How often every member is asked for itself.
 *
 * Slower than this node's own poll on purpose: each refresh is five requests per member -- status,
 * health, and three metrics -- and a three node cluster polled every three seconds would be
 * forty-five requests a minute from a page somebody left open, to answer a question whose answer
 * changes slowly.
 */
const MEMBERS_POLL_MILLIS = 10000;

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
   * Which half of the page is showing.
   *
   * Two halves, because they answer different questions: `health` is "is anything wrong", which is
   * why somebody opens this page in a hurry, and `cluster` is "what is the machinery doing", which
   * is what they read once they know nothing is on fire.
   */
  protected readonly view = signal<'health' | 'cluster'>('health');

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
  private membersPoll?: Subscription;

  /**
   * Every member of the cluster, each one asked for its own account of itself.
   *
   * The page could have taken the membership list out of any single node's answer -- a node knows
   * who its peers are -- but that is one node's opinion of two machines it has not heard from
   * recently. Asking each in turn is the only way to say "alive" and mean it, and it is the
   * difference between a table of members and a table of members that is worth looking at: the
   * uptime, the throughput and the failures in each row are that node's own numbers.
   *
   * Only possible because the front end's proxy can be asked for a specific node. Without it
   * (a dev server, the api on its own domain) the list is empty and the page falls back to
   * whichever node answers, which is what it did before.
   */
  protected readonly members = signal<ClusterMember[]>([]);
  protected readonly membersLoading = signal(false);

  /** A member that is not answering, or is answering badly, is the reason to be on this page. */
  protected readonly unhealthy = computed(() =>
    this.members().filter((one) => one.health !== 'UP' || !one.reachable),
  );

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

  /** The node the detail panels are showing, by the address a person would recognise. */
  protected readonly askedAddress = computed(() => {
    const index = this.node();
    const named = this.members().find((one) => one.index === index);
    return named?.address ?? this.status()?.node.address ?? '';
  });

  /** The only buffers worth a row: an empty queue is never why anybody opened this page. */
  protected readonly droppingBuffers = computed(() =>
    this.buffers().filter((one) => one.dropped > 0),
  );

  /**
   * What this node's traffic is made of, by channel, as shares of one bar.
   *
   * The table underneath says how much each channel carried; only the bar says what the node is
   * mostly *doing* -- whether this is a machine in the middle of a crawl, or one spending its
   * afternoon replicating what another machine crawled. Six numbers in a column never answer that,
   * because the answer is a ratio and columns are read one cell at a time.
   */
  protected readonly trafficMix = computed(() => {
    const carried = this.channels()
      .map((channel) => ({
        name: channel.channel,
        short: channel.channel.replace(/^greenfinger\.|^spreader\./, ''),
        value: channel.counters.sent + channel.counters.received,
        system: channel.systemChannel,
      }))
      .filter((one) => one.value > 0)
      .sort((a, b) => b.value - a.value);
    const total = carried.reduce((sum, one) => sum + one.value, 0) || 1;
    return carried.map((one, rank) => ({
      ...one,
      share: (one.value / total) * 100,
      // a fixed ramp rather than a colour per channel name: the channels are not categories with
      // meanings, they are a ranking, and the ramp says "this one carries more than that one"
      rank: Math.min(rank, 5),
    }));
  });

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

  protected readonly uptime = computed(() => uptimeOf(this.status()?.node.uptimeMillis ?? 0));

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

  constructor() {
    inject(DestroyRef).onDestroy(() => this.poll?.unsubscribe());
    inject(DestroyRef).onDestroy(() => this.membersPoll?.unsubscribe());
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

  /**
   * Ask every node for itself, in parallel.
   *
   * Errors are values here rather than failures: a node that does not answer is exactly what this
   * table exists to show, so it becomes a row saying so instead of emptying the whole list.
   */
  private readMembers(): void {
    const nodes = this.nodes();
    if (!nodes.length) {
      this.members.set([]);
      return;
    }
    this.membersLoading.set(true);
    forkJoin(
      nodes.map((one) =>
        forkJoin({
          status: this.api.clusterStatus(one.index).pipe(catchError(() => of(null))),
          health: this.api.health(one.index).pipe(catchError(() => of(null))),
          heapUsed: this.api
            .metric('jvm.memory.used', one.index, 'area:heap')
            .pipe(catchError(() => of(0))),
          heapMax: this.api
            .metric('jvm.memory.max', one.index, 'area:heap')
            .pipe(catchError(() => of(0))),
          cpu: this.api.metric('process.cpu.usage', one.index).pipe(catchError(() => of(0))),
        }).pipe(map((answers) => this.toMember(one, answers))),
      ),
    ).subscribe({
      next: (members) => {
        this.members.set(members);
        this.membersLoading.set(false);
      },
      error: () => this.membersLoading.set(false),
    });
  }

  private toMember(
    node: ProxyNode,
    answers: {
      status: ClusterStatus | null;
      health: HealthReport | null;
      heapUsed: number;
      heapMax: number;
      cpu: number;
    },
  ): ClusterMember {
    const { status, health } = answers;
    const failures = Object.values(status?.channels ?? {}).reduce(
      (sum, channel) =>
        sum + channel.counters.sendFailures + channel.counters.receiveFailures,
      0,
    );
    return {
      index: node.index,
      address: node.address,
      reachable: status !== null,
      leader: status?.node.leader ?? false,
      nodeId: status?.node.nodeId ?? '',
      onBreak: status?.node.onBreak ?? false,
      memberCount: status?.node.memberCount ?? 0,
      uptime: status ? uptimeOf(status.node.uptimeMillis) : '--',
      tps: status?.summary.totalTps ?? 0,
      failures,
      health: health?.status ?? (status ? 'UNKNOWN' : 'DOWN'),
      heapUsed: answers.heapUsed,
      heapMax: answers.heapMax,
      cpu: answers.cpu,
    };
  }

  /** Heap in use as a percentage of the heap it was given. */
  protected heapPercent(member: ClusterMember): number {
    return member.heapMax > 0 ? Math.round((member.heapUsed / member.heapMax) * 100) : 0;
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
    this.membersPoll?.unsubscribe();
    this.api.health(this.node()).subscribe({
      next: (health) => this.health.set(health),
      error: () => this.health.set({ status: 'DOWN', components: [] }),
    });
    // Every member, on its own slower tick. See MEMBERS_POLL_MILLIS.
    this.membersPoll = interval(MEMBERS_POLL_MILLIS)
      .pipe(startWith(0))
      .subscribe(() => this.readMembers());
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
      .map(([key, value]) => `${key}: ${describeValue(value)}`)
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
