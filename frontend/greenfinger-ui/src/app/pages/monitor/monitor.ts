import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subscription, interval, startWith, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  CatalogDetails,
  CatalogSummary,
  CrawlReport,
  DeleteLayer,
  DeleteLine,
} from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { NotifyService } from '../../core/notify.service';
import { ConfirmDialog, ConfirmData } from '../../shared/confirm-dialog';

/**
 * One catalog, watched: the counters of the run in flight, or of the last one.
 *
 * Also the only place versions can be deleted, and deliberately the only place. The list page is
 * where an operator moves quickly; an irreversible operation belongs on the page they had to open
 * on purpose, next to the numbers that say what is about to go.
 */
@Component({
  selector: 'gf-monitor',
  imports: [
    FormsModule,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatMenuModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatDialogModule,
  ],
  templateUrl: './monitor.html',
  styleUrl: './monitor.scss',
})
export class MonitorPage {
  readonly ref = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly summary = signal<CatalogSummary | null>(null);
  protected readonly details = signal<CatalogDetails | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);

  /** The dry run's report, held so the operator reads it before anything is removed. */
  protected readonly deletePlan = signal<DeleteLine[] | null>(null);
  protected readonly deleteLayers = signal<DeleteLayer[]>(['all']);

  /**
   * Which of the two irreversible things is being asked for.
   *
   * They differ by one thing: whether the catalog itself survives. Both remove every version --
   * the rows, the files, the index and the vectors -- so "empty" leaves a catalog that has been
   * defined and never crawled, ready to be crawled again, and "entirely" leaves nothing at all.
   *
   * Offered only when nothing is being kept. Keeping the three newest versions and deleting the
   * catalog underneath them is not a thing anybody means.
   */
  protected readonly deleteMode = signal<'empty' | 'entirely'>('empty');
  protected readonly keepLatest = signal<number>(3);

  private poll?: Subscription;

  /**
   * Whether the reports have been read since the run ended.
   *
   * Reading them when the crawl stops is not enough on its own: a short crawl can be over before
   * this page opens, and then there is no stopping to notice. The page would sit there with a
   * finished run and no sign of it.
   */
  private reportsRead = false;

  /** Every run of this catalog, newest first. */
  protected readonly reports = signal<CrawlReport[]>([]);
  protected readonly selectedReport = signal<CrawlReport | null>(null);

  /**
   * One entry per run, oldest on the left, for the bar chart.
   *
   * Deduplicated by the moment the run started: every node writes its own copy of the report and
   * they are copied to each other, so a three node cluster leaves three files describing one
   * crawl. Charting all three would draw the same run three times.
   */
  protected readonly runs = computed(() => {
    const byStart = new Map<number, CrawlReport>();
    for (const report of this.reports()) {
      const existing = byStart.get(report.startTime);
      // the copy from the node that published is the one that carries the real ending reason
      if (!existing || report.ending?.published) {
        byStart.set(report.startTime, report);
      }
    }
    return [...byStart.values()].sort((a, b) => a.startTime - b.startTime);
  });

  /** Tallest bar in the chart, so the others can be drawn as a fraction of it. */
  protected readonly runsPeak = computed(() =>
    Math.max(1, ...this.runs().map((r) => r.produced?.savedResourceCount ?? 0)),
  );

  /** What each node contributed to the selected run, largest first. */
  protected readonly nodeShare = computed(() => {
    const report = this.selectedReport() ?? this.runs()[this.runs().length - 1];
    const saved = report?.byNode?.['savedResourceCount'];
    if (!saved) {
      return [];
    }
    const total = Object.values(saved).reduce((sum, value) => sum + value, 0) || 1;
    return Object.entries(saved)
      .map(([node, value]) => ({ node, value, percent: Math.round((value / total) * 100) }))
      .sort((a, b) => b.value - a.value);
  });

  /**
   * The url outcomes of the selected run as segments of one bar.
   *
   * Dispatched is the whole, and the segments are what became of them: handled, and the remainder
   * that was left queued when the run ended. Drawn together because the interesting question is
   * the ratio, and two numbers side by side do not answer it at a glance.
   */
  protected readonly urlSegments = computed(() => {
    const report = this.selectedReport() ?? this.runs()[this.runs().length - 1];
    if (!report?.urls) {
      return [];
    }
    const dispatched = Math.max(1, report.urls.dispatched);
    const segments = [
      { label: 'Handled', value: report.urls.handled, className: 'gf-seg-handled' },
      { label: 'Left over', value: report.urls.outstanding, className: 'gf-seg-outstanding' },
    ];
    return segments
      .filter((segment) => segment.value > 0)
      .map((segment) => ({ ...segment, percent: (segment.value / dispatched) * 100 }));
  });

  protected readonly counters = computed(() => {
    const summary = this.summary();
    if (!summary) {
      return [];
    }
    return [
      { label: 'Pages saved', value: summary.savedResourceCount, icon: 'description', accent: true },
      { label: 'Images saved', value: summary.savedImageCount, icon: 'image' },
      { label: 'Urls dispatched', value: summary.totalUrlCount, icon: 'link' },
      { label: 'Urls handled', value: summary.handledUrlCount ?? 0, icon: 'task_alt' },
      { label: 'Already known', value: summary.existingUrlCount, icon: 'history' },
      { label: 'Filtered out', value: summary.filteredUrlCount, icon: 'filter_alt' },
      { label: 'Invalid', value: summary.invalidUrlCount, icon: 'link_off' },
      { label: 'Duplicate content', value: summary.duplicatedContentCount, icon: 'content_copy' },
      { label: 'Still queued', value: summary.remainingUrlCount, icon: 'pending' },
    ];
  });

  protected readonly progressPercent = computed(() =>
    Math.round((this.summary()?.progress ?? 0) * 100),
  );

  // Two bars rather than one: a crawl ends on whichever limit arrives first, and a single number
  // that is the nearer of the two cannot say which -- 60% of the pages and 60% of the time look
  // identical, and mean quite different things about how much longer this will take.
  protected readonly sizePercent = computed(() =>
    Math.round((this.summary()?.sizeProgress ?? 0) * 100),
  );

  protected readonly timePercent = computed(() =>
    Math.round((this.summary()?.timeProgress ?? 0) * 100),
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.poll?.unsubscribe());
    queueMicrotask(() => this.start());
  }

  private start(): void {
    this.api.getCatalogDetails(this.ref()).subscribe({
      next: (details) => this.details.set(details),
      error: () => undefined,
    });
    this.loadReports();
    // Two seconds while a crawl is live, ten when it is not: the page is often left open for an
    // hour, and a finished catalog does not change under it.
    this.poll = interval(2000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.getSummary(this.ref())),
      )
      .subscribe({
        next: (summary) => {
          const wasLive = this.summary()?.live;
          this.summary.set(summary);
          this.loading.set(false);
          if (summary.live) {
            // it is running again, so whatever it writes when it stops is not read yet
            this.reportsRead = false;
          } else {
            // a run that has just ended has just written its report -- and so has one that ended
            // before this page was opened
            if (wasLive || !this.reportsRead) {
              this.reportsRead = true;
              this.loadReports();
            }
            this.slowDown();
          }
        },
        error: (failure) => {
          this.loading.set(false);
          this.notify.failed(failure, 'Could not read the run');
        },
      });
  }

  /** Read once, and again when a run ends: a report only appears after the crawl is over. */
  protected loadReports(): void {
    this.api.crawlReports(this.ref()).subscribe({
      next: (reports) => {
        this.reports.set(reports);
        if (!this.selectedReport() && reports.length) {
          this.selectedReport.set(reports[0]);
        }
      },
      error: () => undefined,
    });
  }

  protected selectReport(report: CrawlReport): void {
    this.selectedReport.set(report);
  }

  protected reportLabel(report: CrawlReport): string {
    return new Date(report.startTime).toLocaleString();
  }

  private slowDown(): void {
    this.poll?.unsubscribe();
    this.poll = interval(10000)
      .pipe(switchMap(() => this.api.getSummary(this.ref())))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary);
          if (summary.live) {
            // something started again, from here or from the command line: back to two seconds
            this.poll?.unsubscribe();
            this.start();
            return;
          }
          // and the reports, every slow tick. Watching for the run to stop is not enough on its
          // own: a short crawl can begin and end between two ten second polls, so the page never
          // sees it running and never notices it stopping. This also picks up runs started from
          // the command line while the page was open.
          this.loadReports();
        },
        error: () => undefined,
      });
  }

  // ---- the verbs ------------------------------------------------------------------------

  protected crawl(): void {
    this.act(this.api.crawl(this.ref()), 'Crawl started');
  }

  protected update(): void {
    this.act(this.api.update(this.ref()), 'Update started');
  }

  protected rebuild(): void {
    this.act(this.api.rebuild(this.ref()), 'Rebuild started: a new version, nothing deleted');
  }

  protected interrupt(): void {
    this.act(this.api.interrupt(this.ref()), 'Asked the crawl to stop');
  }

  protected replay(): void {
    this.act(this.api.replay(this.ref()), 'Replaying index and vectors from disk');
  }

  protected restoreFiles(): void {
    // a different kind of replay: the other layers are rebuilt from the database, which has
    // everything they need, but files have to be fetched again from the urls the rows record
    this.act(
      this.api.replay(this.ref(), ['file']),
      'Fetching the missing pages and pictures again from their urls',
    );
  }

  private act(call: Observable<unknown>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.notify.ok(message);
      },
      error: (failure) => {
        this.busy.set(false);
        this.notify.failed(failure);
      },
    });
  }

  // ---- deleting versions ----------------------------------------------------------------

  protected toggleLayer(layer: DeleteLayer): void {
    // 'all' and the individual layers are two ways of saying it, so picking one clears the other
    if (layer === 'all') {
      this.deleteLayers.set(['all']);
      this.deletePlan.set(null);
      return;
    }
    this.deleteLayers.update((current) => {
      const without = current.filter((one) => one !== 'all');
      const next = without.includes(layer) ? without.filter((one) => one !== layer) : [...without, layer];
      return next.length ? next : ['all'];
    });
    this.deletePlan.set(null);
  }

  protected setDeleteMode(mode: 'empty' | 'entirely'): void {
    this.deleteMode.set(mode);
    this.deletePlan.set(null);
  }

  /** Removing the catalog itself is only offered when no version is being kept. */
  protected readonly canDeleteEntirely = computed(() => this.keepLatest() === 0);

  /**
   * What the three choices actually send.
   *
   * Keeping nothing is a whole-catalog operation and names no versions at all: the api reads an
   * absent version and an absent keepLatest as "all of it", and sending keepLatest 0 instead asks
   * for each version by name -- which is refused for the one search is currently serving, so both
   * whole-catalog choices would fail on a catalog that has ever finished a crawl. Keeping some is
   * the ordinary case and does name them. Purge is what separates emptying from deleting: it is
   * the catalog row itself, and it goes only when the operator chose to delete entirely.
   */
  private deleteOptions(dryRun: boolean) {
    const wholeCatalog = this.keepLatest() === 0;
    return {
      ...(wholeCatalog ? {} : { keepLatest: this.keepLatest() }),
      layers: this.deleteLayers(),
      ...(wholeCatalog && this.deleteMode() === 'entirely' ? { purge: true } : {}),
      dryRun,
    };
  }

  /** Always a dry run first. The report is what the confirmation is then asked about. */
  protected planDelete(): void {
    this.busy.set(true);
    this.api
      .deleteVersions(this.ref(), this.deleteOptions(true))
      .subscribe({
        next: (lines) => {
          this.busy.set(false);
          this.deletePlan.set(lines);
          if (lines.length === 0) {
            this.notify.ok('Nothing to remove: there are no versions beyond the ones you are keeping');
          }
        },
        error: (failure) => {
          this.busy.set(false);
          this.notify.failed(failure);
        },
      });
  }

  protected applyDelete(): void {
    const lines = this.deletePlan() ?? [];
    const total = lines.reduce((sum, line) => sum + line.count, 0);
    const entirely = this.deleteMode() === 'entirely' && this.canDeleteEntirely();
    const data: ConfirmData = {
      title: entirely ? `Delete '${this.ref()}' entirely?` : `Empty '${this.ref()}'?`,
      message:
        `${total} item(s) will be removed from ${this.deleteLayers().join(', ')}, keeping the ` +
        `${this.keepLatest()} most recent version(s).\n\n` +
        (entirely
          ? 'The catalog goes too, so there is nothing left to crawl, search or replay.\n\n'
          : 'The catalog itself stays, defined and with nothing crawled, ready to be crawled ' +
            'again.\n\n') +
        'This cannot be undone. The index and the vectors can be replayed from the database; ' +
        'files can only be fetched again from the site, and only while it still serves them.',
      confirmLabel: entirely ? 'Delete entirely' : 'Empty it',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '32rem' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busy.set(true);
        this.api
          .deleteVersions(this.ref(), this.deleteOptions(false))
          .subscribe({
            next: (result) => {
              const removed = result.reduce((sum, line) => sum + line.count, 0);
              this.deletePlan.set(null);
              if (!entirely) {
                this.busy.set(false);
                this.notify.ok(`Removed ${removed} item(s)`);
                return;
              }
              // The catalog itself is the last thing to go, and only once its data has: deleting
              // the row first would leave the files, the index and the vectors with nothing that
              // names them, and no way to ask for them to be removed.
              this.api.deleteCatalog(this.ref()).subscribe({
                next: () => {
                  this.busy.set(false);
                  this.notify.ok(`Removed ${removed} item(s), and '${this.ref()}' with them`);
                  this.router.navigate(['/catalogs']);
                },
                error: (failure) => {
                  this.busy.set(false);
                  this.notify.failed(failure);
                },
              });
            },
            error: (failure) => {
              this.busy.set(false);
              this.notify.failed(failure);
            },
          });
      });
  }

  protected edit(): void {
    this.router.navigate(['/catalogs', this.ref(), 'edit']);
  }
}
