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
  HealthComponent,
  HealthReport,
  NodeReading,
  ProxyNode,
} from '../../core/api.models';
import { Sparkline } from '../../shared/sparkline';

/**
 * A component's report as flat key/value. One level of nesting becomes `parent.child`; deeper is
 * left as json, because nothing here goes deeper.
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
 * One health-check value as a phrase. The spreader check carries its whole membership, which the
 * table above already lays out -- here it only needs to say how many.
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
 * Slower than this node's own poll: each refresh is several requests per member, and membership
 * changes slowly.
 */
const MEMBERS_POLL_MILLIS = 10000;

/**
 * How this node and its stores are doing. One node's view at a time, on purpose -- presenting it
 * as the cluster's would hide the node that had stopped working.
 *
 * The numbers worth a page are the ones that produce no log line: a full inbound buffer discards
 * silently by design, and a node that cannot see a leader is running, answering and doing nothing.
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

  /** `health` is "is anything wrong"; `cluster` is "what is the machinery doing". */
  protected readonly view = signal<'health' | 'cluster'>('health');

  /**
   * Which node is being asked. Without pinning, consecutive polls come from different machines and
   * the counters jump. Empty where the app is not served by its own proxy, and then unpinned.
   */
  protected readonly nodes = signal<ProxyNode[]>([]);
  protected readonly node = signal<number | null>(null);

  protected readonly healthStatus = computed(() => this.health()?.status ?? '');

  /**
   * Throughput over the life of the page. The endpoint reports a rate with no memory, so the
   * samples are kept here to make a shape; lost on reload, which is the cost of not storing them.
   */
  protected readonly tpsHistory = signal<number[]>([]);
  private readonly channelHistory = signal<Record<string, number[]>>({});

  private poll?: Subscription;
  private membersPoll?: Subscription;

  /**
   * Every member, each asked for its own account. One node's list of peers is its opinion of
   * machines it may not have heard from; asking each is the only way to say "alive" and mean it.
   * Needs the proxy's per-node pinning, and is empty without it.
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

  /** Every component invents its own keys, so key=value rather than columns that never line up. */
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
   * Traffic by channel as shares of one bar: the table says how much each carried, the bar says
   * what the node is mostly doing. A ratio is not readable as a column of numbers.
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

  /** Errors are values: a node that does not answer is what this table exists to show. */
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
          // one call for all three: the actuator's metrics endpoint is not exposed in
          // production, which is why this column used to be empty there
          reading: this.api
            .node(one.index)
            .pipe(catchError(() => of({ heapUsed: 0, heapMax: 0, cpu: 0 }))),
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
      reading: NodeReading;
    },
  ): ClusterMember {
    const { status, health, reading } = answers;
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
      heapUsed: reading.heapUsed,
      heapMax: reading.heapMax,
      cpu: reading.cpu,
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

  /** Each check invents its own keys, so one line of key=value rather than a table. */
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
