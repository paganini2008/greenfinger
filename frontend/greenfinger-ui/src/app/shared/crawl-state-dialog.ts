import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../core/api.service';
import { RocksDbUsage } from '../core/api.models';

export interface CrawlStateData {
  catalogId: string;
  catalogName: string;
}

/**
 * The half of a crawl that nothing else shows: the frontier, and the two dedup filters.
 *
 * They are RocksDB directories under the system data directory, one per catalog and version.
 * Nothing reads them but the crawl, no page lists them and no log mentions their size -- which is
 * how a machine ends up full of the state of catalogs nobody has run in months.
 *
 * The key counts are missing while that catalog is being crawled, because RocksDB lets one
 * process hold a store open and the crawl is holding it. The size is measured either way, since
 * that is what somebody worried about a disk actually came to see.
 */
@Component({
  selector: 'gf-crawl-state-dialog',
  imports: [DecimalPipe, MatButtonModule, MatDialogModule, MatIconModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title class="flex items-center gap-2">
      <mat-icon>storage</mat-icon>
      Crawl state &mdash; {{ data.catalogName }}
    </h2>
    <mat-dialog-content>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (usage(); as usage) {
        <p class="gf-dialog-note">
          What the crawler keeps to know where it is: the frontier holds what is left to fetch, and
          the two filters hold what has already been seen. Every version this catalog still has on
          disk is counted.
        </p>

        <div class="gf-state-totals">
          <div>
            <span class="gf-state-value">{{ bytes(usage.bytes) }}</span>
            <span class="gf-state-label">on disk</span>
          </div>
          <div>
            <span class="gf-state-value">
              {{ usage.crawlRunning ? '--' : (usage.keyCount | number) }}
            </span>
            <span class="gf-state-label">keys</span>
          </div>
        </div>

        @if (usage.crawlRunning) {
          <p class="gf-dialog-warn">
            <mat-icon>info</mat-icon>
            This catalog is being crawled, and the crawl has the stores open. RocksDB allows one
            process at a time, so the key counts cannot be read until it finishes.
          </p>
        }

        <table class="gf-table">
          <thead>
            <tr>
              <th>Store</th>
              <th class="gf-num">Keys</th>
              <th class="gf-num">Size</th>
            </tr>
          </thead>
          <tbody>
            @for (store of usage.stores; track store.name) {
              <tr>
                <td>
                  <div>{{ store.name }}</div>
                  <div class="gf-state-path">{{ store.path }}</div>
                </td>
                <td class="gf-num">
                  {{ store.keyCount < 0 ? '--' : (store.keyCount | number) }}
                </td>
                <td class="gf-num">{{ bytes(store.bytes) }}</td>
              </tr>
            }
          </tbody>
        </table>

        <p class="gf-dialog-note">
          The counts are RocksDB's own estimate. It counts entries not yet compacted away, so a
          store that has had a lot deleted reads high until compaction catches up.
        </p>
      } @else if (!loading()) {
        <p class="gf-dialog-note">The stores could not be measured.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .gf-state-totals {
      display: flex;
      gap: 2.5rem;
      margin: 1rem 0;
    }

    .gf-state-totals > div {
      display: flex;
      flex-direction: column;
    }

    .gf-state-value {
      font: var(--mat-sys-headline-small);
      color: var(--gf-green);
    }

    .gf-state-label {
      color: var(--gf-muted);
      font: var(--mat-sys-label-large);
    }

    .gf-state-path {
      color: var(--gf-muted);
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 0.78rem;
      overflow-wrap: anywhere;
    }

    .gf-dialog-note {
      color: var(--gf-muted);
      font: var(--mat-sys-body-medium);
    }

    .gf-dialog-warn {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      padding: 0.6rem 0.75rem;
      border-radius: 0.5rem;
      background: color-mix(in srgb, var(--mat-sys-tertiary) 15%, transparent);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class CrawlStateDialog {
  protected readonly data = inject<CrawlStateData>(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<CrawlStateDialog>);
  private readonly api = inject(ApiService);

  protected readonly usage = signal<RocksDbUsage | null>(null);
  protected readonly loading = signal(true);

  constructor() {
    this.api.rocksDbUsage(this.data.catalogId).subscribe({
      next: (usage) => {
        this.usage.set(usage);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected bytes(value: number): string {
    if (value <= 0) {
      return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const power = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)));
    return `${(value / Math.pow(1024, power)).toFixed(power === 0 ? 0 : 1)} ${units[power]}`;
  }
}
