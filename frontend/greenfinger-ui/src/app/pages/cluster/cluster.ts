import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ClusterChannel, ClusterStatus } from '../../core/api.models';

/**
 * What this node thinks of the cluster it is in.
 *
 * This node's view alone, and deliberately so: every node answers only for itself, and a page
 * that presented one node's numbers as the cluster's would hide precisely the node that had
 * stopped working. Which node is being asked is at the top of the page.
 *
 * The numbers worth a page of their own are the ones that produce no log line. A full inbound
 * buffer discards messages silently -- that is the design, because blocking the producer would
 * take the whole dispatch chain down with it -- so a non-zero dropped count is real work lost and
 * nothing else will ever mention it. Likewise a node that can no longer see a leader is running,
 * answering, and doing nothing.
 */
@Component({
  selector: 'gf-cluster',
  imports: [DecimalPipe, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  templateUrl: './cluster.html',
  styleUrl: './cluster.scss',
})
export class ClusterPage {
  private readonly api = inject(ApiService);

  protected readonly status = signal<ClusterStatus | null>(null);
  protected readonly health = signal<string>('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  private poll?: Subscription;

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
    for (const channel of this.channels()) {
      const dropped = channel.buffer?.dropped ?? 0;
      if (dropped > 0) {
        warnings.push(
          `${channel.channel} has dropped ${dropped} message(s). Dropping is silent by design, so this is the only place it shows.`,
        );
      }
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

  constructor() {
    inject(DestroyRef).onDestroy(() => this.poll?.unsubscribe());
    queueMicrotask(() => this.start());
  }

  private start(): void {
    this.api.health().subscribe({
      next: (health) => this.health.set(health.status),
      error: () => this.health.set('DOWN'),
    });
    // three seconds: throughput is a rate and a rate needs a window, but nobody watches this
    // page for long enough to want it faster
    this.poll = interval(3000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.clusterStatus()),
      )
      .subscribe({
        next: (status) => {
          this.status.set(status);
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

  protected bufferPercent(channel: ClusterChannel): number {
    const buffer = channel.buffer;
    if (!buffer || !buffer.capacity) {
      return 0;
    }
    return Math.min(100, Math.round((buffer.pending / buffer.capacity) * 100));
  }
}
