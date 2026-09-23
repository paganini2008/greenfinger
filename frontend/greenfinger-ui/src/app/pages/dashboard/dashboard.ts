import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Subscription, catchError, forkJoin, interval, of, startWith, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Catalog, CatalogSummary, CrawlStatus, StorageUsage } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';

/** How often the counters are read while something is crawling, and while nothing is. */
const LIVE_POLL_MILLIS = 2000;
const IDLE_POLL_MILLIS = 15000;

/** How many catalogs are asked for their last run when choosing what the sieve shows. */
const FEATURED_CANDIDATES = 8;

/**
 * The installation at a glance: what it is doing now, what it kept, and what it threw away.
 *
 * The page is built around the one picture that belongs to a crawler and to nothing else on a
 * dashboard: the **sieve**. A crawl touches far more of the web than it keeps -- it goes out of
 * scope, it has been seen before, it is over the limit -- and the ratio between what was seen and
 * what survived is the single most useful thing an operator can be told. A crawl that filtered out
 * nine urls in ten has a scope problem, and no column of counters ever says so.
 *
 * Everything else here is deliberately quiet. One number is the headline; the rest is a ledger.
 */
@Component({
  selector: 'gf-dashboard',
  imports: [
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly catalogs = signal<Catalog[]>([]);
  protected readonly statuses = signal<CrawlStatus[]>([]);
  protected readonly storage = signal<StorageUsage | null>(null);
  protected readonly featured = signal<CatalogSummary | null>(null);
  protected readonly loading = signal(true);

  private poll?: Subscription;

  protected readonly running = computed(() => this.statuses().filter((one) => one.running));

  /**
   * The run the sieve is drawn from: whatever is crawling, or the last one that finished.
   *
   * A dashboard that showed nothing until somebody pressed Crawl would be blank almost all of the
   * time, and the last run is what an operator wants to see when they come back in the morning.
   */
  protected readonly featuredName = computed(() => this.featured()?.catalogName ?? '');

  protected readonly live = computed(() => this.featured()?.live === true);

  /**
   * What became of every url the featured run touched, largest share first.
   *
   * Saved is the only green band. The three that follow are shades of "seen and deliberately not
   * kept", which is a different thing from a failure and is coloured differently from one.
   */
  protected readonly sieve = computed(() => {
    const run = this.featured();
    if (!run) {
      return [];
    }
    const bands = [
      { key: 'saved', label: 'kept as pages', value: run.savedResourceCount ?? 0 },
      { key: 'known', label: 'already known', value: run.existingUrlCount ?? 0 },
      { key: 'filtered', label: 'out of scope', value: run.filteredUrlCount ?? 0 },
      { key: 'dropped', label: 'over the limit', value: run.abandonedUrlCount ?? 0 },
      { key: 'duplicate', label: 'same page twice', value: run.duplicatedContentCount ?? 0 },
      { key: 'invalid', label: 'never answered', value: run.invalidUrlCount ?? 0 },
      { key: 'queued', label: 'still queued', value: run.remainingUrlCount ?? 0 },
    ].filter((band) => band.value > 0);
    const total = bands.reduce((sum, band) => sum + band.value, 0) || 1;
    return bands.map((band) => ({ ...band, share: (band.value / total) * 100 }));
  });

  protected readonly seen = computed(() =>
    this.sieve().reduce((sum, band) => sum + band.value, 0),
  );

  protected readonly kept = computed(() => this.featured()?.savedResourceCount ?? 0);

  /**
   * "One page in every n urls", which is the sentence the bar is drawing.
   *
   * Null when nothing was kept, because "one in infinity" is not a thing to print at somebody.
   */
  protected readonly keepRate = computed(() => {
    const kept = this.kept();
    return kept > 0 ? Math.round(this.seen() / kept) : null;
  });

  /** Catalogs that have finished a crawl at least once, newest first. */
  protected readonly recent = computed(() =>
    [...this.catalogs()]
      .filter((one) => (one.indexVersion ?? 0) >= 0)
      .sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
      .slice(0, 6),
  );

  protected readonly searchable = computed(
    () =>
      this.catalogs().filter(
        (one) => (one.searchVersion ?? -1) >= 0 && (one.outputTypes ?? []).includes('index'),
      ).length,
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.poll?.unsubscribe());
    queueMicrotask(() => this.start(IDLE_POLL_MILLIS));
    this.api.storageUsage().subscribe({
      next: (usage) => this.storage.set(usage),
      error: () => undefined,
    });
  }

  private start(every: number): void {
    this.poll?.unsubscribe();
    this.poll = interval(every)
      .pipe(
        startWith(0),
        switchMap(() => forkJoin({ catalogs: this.api.listCatalogs(), statuses: this.api.status() })),
      )
      .subscribe({
        next: ({ catalogs, statuses }) => {
          this.catalogs.set(catalogs);
          this.statuses.set(statuses);
          this.loading.set(false);
          this.readFeatured(catalogs, statuses);
          // two seconds while a crawl is moving, fifteen when nothing is: an idle installation
          // should not be asking two questions a second for the rest of the afternoon
          const wanted = statuses.some((one) => one.running) ? LIVE_POLL_MILLIS : IDLE_POLL_MILLIS;
          if (wanted !== every) {
            this.start(wanted);
          }
        },
        error: () => this.loading.set(false),
      });
  }

  /**
   * The summary behind the sieve: the running catalog, else the last one that actually ran.
   *
   * Ordered by when each run *ended*, which the summary reports, rather than by the catalog's
   * `lastModified` -- a field this api has never sent, so sorting by it left the
   * order untouched and the sieve showed whichever catalog happened to be created first. A field
   * that is always null sorts everything equally and silently.
   *
   * A catalog with no pages is skipped rather than featured: the empty state belongs to an
   * installation that has never crawled, not to one whose newest catalog is untouched.
   */
  private readFeatured(catalogs: Catalog[], statuses: CrawlStatus[]): void {
    const running = statuses.find((one) => one.running);
    if (running) {
      this.api.getSummary(running.name).subscribe({
        next: (summary) => this.featured.set(summary),
        error: () => this.featured.set(null),
      });
      return;
    }
    const named = catalogs.filter((one) => !!one.name).slice(0, FEATURED_CANDIDATES);
    if (!named.length) {
      this.featured.set(null);
      return;
    }
    forkJoin(
      named.map((one) => this.api.getSummary(one.name!).pipe(catchError(() => of(null)))),
    ).subscribe({
      next: (summaries) => {
        const ran = summaries
          .filter((one): one is CatalogSummary => (one?.savedResourceCount ?? 0) > 0)
          .sort((a, b) => (b.endTime ?? 0) - (a.endTime ?? 0));
        this.featured.set(ran[0] ?? null);
      },
      error: () => this.featured.set(null),
    });
  }

  /**
   * Where the pages are, said the way somebody would say it.
   *
   * The store names itself `replicated:local` or `minio`, which is precise and is not English.
   * The question a person is asking is whether this is on the machine or in an object store.
   */
  protected readonly where = computed(() => {
    const target = this.storage()?.target ?? '';
    if (target.includes('minio')) {
      return 'in MinIO';
    }
    if (target.includes('local')) {
      return 'on disk';
    }
    return target ? `in ${target}` : 'on disk';
  });

  /** Bytes as a person would say them. */
  protected bytes(value: number | undefined): string {
    if (!value || value <= 0) {
      return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const power = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)));
    return `${(value / Math.pow(1024, power)).toFixed(power === 0 ? 0 : 1)} ${units[power]}`;
  }
}
