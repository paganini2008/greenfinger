import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, Subscription, forkJoin, interval, startWith, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Catalog, CrawlStatus } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { NotifyService } from '../../core/notify.service';
import { ConfirmDialog, ConfirmData } from '../../shared/confirm-dialog';
import { CrawlStateDialog } from '../../shared/crawl-state-dialog';

/** How hard reload() tries to see its own write, and how long it waits between attempts. */
const RELOAD_RETRIES = 4;
const RELOAD_RETRY_MILLIS = 500;

/**
 * The catalog list: what exists, what is running, and every verb that acts on one.
 *
 * Progress comes from polling /crawl/status rather than from a socket. A crawl reports in seconds
 * and often runs for hours, so a three second poll of one small endpoint costs nothing and removes
 * a whole class of reconnect handling. The poll only runs while something is actually crawling --
 * a page of finished catalogs sits still.
 */
@Component({
  selector: 'gf-catalogs',
  imports: [
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatChipsModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatDialogModule,
  ],
  templateUrl: './catalogs.html',
  styleUrl: './catalogs.scss',
})
export class CatalogsPage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly catalogs = signal<Catalog[]>([]);
  protected readonly statuses = signal<Record<string, CrawlStatus>>({});
  protected readonly categories = signal<string[]>([]);
  protected readonly loading = signal(true);
  protected readonly busyId = signal<string | null>(null);

  protected readonly keyword = signal('');
  protected readonly category = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(9);

  private poll?: Subscription;

  protected readonly filtered = computed(() => {
    const keyword = this.keyword().trim().toLowerCase();
    const category = this.category();
    return this.catalogs().filter((catalog) => {
      if (category && catalog.cat !== category) {
        return false;
      }
      if (!keyword) {
        return true;
      }
      return `${catalog.name} ${catalog.url}`.toLowerCase().includes(keyword);
    });
  });

  /** Paged in the browser: a deployment has tens of catalogs, not thousands. */
  protected readonly visible = computed(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.filtered().slice(start, start + this.pageSize());
  });

  protected readonly runningCount = computed(
    () => Object.values(this.statuses()).filter((status) => status.running).length,
  );

  protected readonly anyRunning = computed(() => this.runningCount() > 0);

  /**
   * What is in the way, if anything. Only one crawl runs at a time across every node, so a
   * catalog being crawled is the reason every other catalog's verbs are refused -- and the
   * server refuses them with that same sentence, so the button says it before the click rather
   * than the snackbar afterwards.
   */
  protected readonly busyWith = computed(
    () => Object.values(this.statuses()).find((status) => status.running)?.name ?? '',
  );

  /** True for every catalog except the one actually running. */
  protected blockedBy(catalog: Catalog): string {
    const running = this.busyWith();
    return running && running !== catalog.name ? running : '';
  }

  /**
   * How many catalogs the Search page can actually find anything in.
   *
   * Both halves are needed and neither is enough. `searchVersion` is the version that finished and
   * was published, and it is set whatever the outputs were -- so counting it alone promised search
   * on catalogs written to files and nothing else, and the search page then had to explain a
   * result of zero as though the query were at fault. The index output is what puts words
   * anywhere they can be looked up.
   */
  protected readonly searchableCount = computed(
    () =>
      this.catalogs().filter(
        (catalog) =>
          (catalog.searchVersion ?? -1) >= 0 && (catalog.outputTypes ?? []).includes('index'),
      ).length,
  );

  constructor() {
    // catalog-edit names what it just wrote, so the first read can wait for that node's copy
    const saved = this.route.snapshot.queryParamMap.get('saved');
    this.reload(saved ? (rows) => rows.some((row) => row.id === saved || row.name === saved) : undefined);
    inject(DestroyRef).onDestroy(() => this.stopPolling());
    // Poll only while something is moving, and stop the moment nothing is: an idle page should
    // not be sending a request every three seconds for the rest of the afternoon.
    effect(() => {
      const running = this.anyRunning();
      if (running && !this.poll) {
        this.poll = interval(3000)
          .pipe(
            startWith(0),
            switchMap(() => this.api.status()),
          )
          .subscribe({
            next: (rows) => this.acceptStatus(rows),
            error: () => this.stopPolling(),
          });
      } else if (!running) {
        this.stopPolling();
      }
    });
  }

  /**
   * Read the list again.
   *
   * `settled` is what makes this safe to call straight after a write. The front end spreads /v2
   * across every node and replication between them is asynchronous, so a delete answered by one
   * node and a list served by another can genuinely disagree for a moment -- which showed up as a
   * deleted catalog still sitting on the page. Given a predicate, this retries a few times until
   * the list agrees with what was just done, rather than painting a stale answer and stopping.
   */
  protected reload(settled?: (catalogs: Catalog[]) => boolean, attempt = 0): void {
    this.loading.set(true);
    forkJoin({
      catalogs: this.api.listCatalogs(),
      categories: this.api.listCategories(),
      statuses: this.api.status(),
    }).subscribe({
      next: ({ catalogs, categories, statuses }) => {
        if (settled && !settled(catalogs) && attempt < RELOAD_RETRIES) {
          // another node has not caught up yet; ask again rather than show what it still thinks
          setTimeout(() => this.reload(settled, attempt + 1), RELOAD_RETRY_MILLIS);
          return;
        }
        this.catalogs.set(catalogs);
        this.categories.set(categories);
        this.acceptStatus(statuses);
        this.loading.set(false);
      },
      error: (failure) => {
        this.loading.set(false);
        this.notify.failed(failure, 'Could not load the catalogs');
      },
    });
  }

  protected statusOf(catalog: Catalog): CrawlStatus | undefined {
    return catalog.id ? this.statuses()[catalog.id] : undefined;
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  // ---- the verbs ------------------------------------------------------------------------

  protected crawl(catalog: Catalog): void {
    this.run(catalog, this.api.crawl(catalog.name!), 'Crawl started');
  }

  protected update(catalog: Catalog): void {
    this.run(catalog, this.api.update(catalog.name!), 'Update started');
  }

  protected rebuild(catalog: Catalog): void {
    this.run(catalog, this.api.rebuild(catalog.name!), 'Rebuild started: a new version, nothing deleted');
  }

  protected interrupt(catalog: Catalog): void {
    this.run(catalog, this.api.interrupt(catalog.name!), 'Asked the crawl to stop');
  }

  protected replay(catalog: Catalog): void {
    this.run(
      catalog,
      this.api.replay(catalog.name!),
      'Replaying the index and vectors from what is already on disk',
    );
  }

  /** The frontier and dedup sizes, which nothing else in the app shows. */
  protected showState(catalog: Catalog): void {
    this.dialog.open(CrawlStateDialog, {
      data: { catalogId: catalog.id!, catalogName: catalog.name! },
      width: '38rem',
    });
  }

  protected remove(catalog: Catalog): void {
    const data: ConfirmData = {
      title: `Delete '${catalog.name}'?`,
      message:
        'This removes the crawl definition only.\n\n' +
        'The pages it crawled stay where they are, in the files, the index and the vector store. ' +
        'Removing those is a separate operation, on the Monitor page.',
      confirmLabel: 'Delete definition',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '30rem' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.api.deleteCatalog(catalog.name!).subscribe({
            next: () => {
              this.notify.ok(`'${catalog.name}' deleted`);
              // gone from this node's answer; keep asking until whichever node serves the list
              // agrees, so the row does not sit there looking undeleted
              this.catalogs.update((rows) => rows.filter((row) => row.id !== catalog.id));
              this.reload((rows) => !rows.some((row) => row.id === catalog.id));
            },
            error: (failure) => this.notify.failed(failure),
          });
        }
      });
  }

  private run(catalog: Catalog, call: Observable<unknown>, message: string): void {
    this.busyId.set(catalog.id ?? null);
    call.subscribe({
      next: () => {
        this.busyId.set(null);
        this.notify.ok(message);
        // the run starts on a background thread, so the first status is a moment behind the call
        setTimeout(() => this.refreshStatus(), 400);
      },
      error: (failure) => {
        this.busyId.set(null);
        this.notify.failed(failure);
      },
    });
  }

  private refreshStatus(): void {
    this.api.status().subscribe({
      next: (rows) => this.acceptStatus(rows),
      error: () => undefined,
    });
  }

  private acceptStatus(rows: CrawlStatus[]): void {
    const byId: Record<string, CrawlStatus> = {};
    for (const row of rows) {
      byId[row.id] = row;
    }
    this.statuses.set(byId);
  }

  private stopPolling(): void {
    this.poll?.unsubscribe();
    this.poll = undefined;
  }
}
