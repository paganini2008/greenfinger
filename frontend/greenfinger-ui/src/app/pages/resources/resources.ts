import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Catalog, ResourceImageView, ResourceRow } from '../../core/api.models';
import { NotifyService } from '../../core/notify.service';

/**
 * What a crawl actually stored, to be looked through.
 *
 * The Search page and this one answer different questions and neither replaces the other. Search
 * ranks pages by what they are about, from the version being served. This lists rows from the
 * table in crawl order, for any version -- including one that was never published, which is the
 * version somebody wants when a crawl came back wrong and they are trying to see what it got.
 *
 * A catalog has to be chosen before anything is fetched. "Every resource in the database" is not
 * a question anybody asks and is a table scan for whoever tries.
 */
@Component({
  selector: 'gf-resources',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './resources.html',
  styleUrl: './resources.scss',
})
export class ResourcesPage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly catalogs = signal<Catalog[]>([]);
  protected readonly versions = signal<number[]>([]);
  protected readonly rows = signal<ResourceRow[]>([]);

  protected readonly catalogId = signal<string>('');
  protected readonly version = signal<number | null>(null);
  protected readonly keyword = signal<string>('');
  protected readonly from = signal<Date | null>(null);
  protected readonly to = signal<Date | null>(null);
  protected readonly sort = signal<'asc' | 'desc'>('desc');

  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(25);
  protected readonly total = signal(0);

  protected readonly loading = signal(false);
  protected readonly expanded = signal<string | null>(null);

  /**
   * Thumbnails, by image id.
   *
   * The image endpoint wants the bearer token and an `<img src>` sends none, so the bytes are
   * fetched through the interceptor that holds it and handed to the tag as an object url. Only
   * for the row that is open: a page of twenty five rows with a dozen pictures each is three
   * hundred requests nobody asked for.
   */
  protected readonly thumbnails = signal<Map<string, string>>(new Map());
  protected readonly missingThumbnails = signal<Set<string>>(new Set());

  /** Typing in the keyword box should not be one request per keystroke. */
  private readonly typed = new Subject<void>();

  protected readonly catalogName = computed(
    () => this.catalogs().find((c) => c.id === this.catalogId())?.name ?? '',
  );

  protected readonly filtered = computed(
    () => !!this.keyword() || this.version() !== null || !!this.from() || !!this.to(),
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.typed.complete();
      this.releaseThumbnails();
    });
    this.typed.pipe(debounceTime(300)).subscribe(() => this.reload(true));

    this.api.listCatalogs().subscribe({
      next: (catalogs) => {
        this.catalogs.set(catalogs);
        // deep linked from a catalog tile, or simply the first one there is: a page that opens
        // on an empty picker looks broken when there is only ever one answer
        const wanted = this.route.snapshot.queryParamMap.get('catalog');
        const chosen =
          catalogs.find((c) => c.id === wanted || c.name === wanted) ?? catalogs[0];
        if (chosen?.id) {
          this.catalogId.set(chosen.id);
        }
      },
      error: (failure) => this.notify.failed(failure),
    });

    effect(() => {
      const id = this.catalogId();
      if (!id) {
        return;
      }
      this.api.resourceVersions(id).subscribe({
        next: (versions) => this.versions.set(versions),
        error: () => this.versions.set([]),
      });
      this.reload(true);
    });
  }

  protected onCatalog(id: string): void {
    this.catalogId.set(id);
    this.version.set(null);
    // the url is the state worth keeping: a link to this page is a link to this catalog
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { catalog: id },
      replaceUrl: true,
    });
  }

  protected onKeyword(value: string): void {
    this.keyword.set(value);
    this.typed.next();
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.reload(false);
  }

  protected clear(): void {
    this.keyword.set('');
    this.version.set(null);
    this.from.set(null);
    this.to.set(null);
    this.reload(true);
  }

  /** @param rewind whether the filter changed, in which case page four of the old one is gone. */
  protected reload(rewind: boolean): void {
    const id = this.catalogId();
    if (!id) {
      return;
    }
    if (rewind) {
      this.pageIndex.set(0);
    }
    this.loading.set(true);
    this.api
      .resources({
        catalogId: id,
        version: this.version(),
        q: this.keyword().trim(),
        // the end of the chosen day rather than its midnight, or "to the 5th" excludes the 5th
        from: this.from()?.toISOString() ?? null,
        to: this.to() ? endOfDay(this.to()!).toISOString() : null,
        page: this.pageIndex(),
        size: this.pageSize(),
        sort: this.sort(),
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.results);
          this.total.set(page.total);
          this.loading.set(false);
        },
        error: (failure) => {
          this.loading.set(false);
          this.notify.failed(failure);
        },
      });
  }

  protected toggleSort(): void {
    this.sort.set(this.sort() === 'desc' ? 'asc' : 'desc');
    this.reload(false);
  }

  protected toggle(row: ResourceRow): void {
    const opening = this.expanded() !== row.id;
    this.releaseThumbnails();
    this.expanded.set(opening ? row.id : null);
    if (opening) {
      this.loadThumbnails(row);
    }
  }

  protected thumbnail(image: ResourceImageView): string | undefined {
    return this.thumbnails().get(image.imageId);
  }

  protected isMissing(image: ResourceImageView): boolean {
    return this.missingThumbnails().has(image.imageId);
  }

  /** Bytes as a person would say them. */
  protected bytes(value: number | null): string {
    if (!value || value <= 0) {
      return '--';
    }
    const units = ['B', 'KB', 'MB'];
    const power = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)));
    return `${(value / Math.pow(1024, power)).toFixed(power === 0 ? 0 : 1)} ${units[power]}`;
  }

  private loadThumbnails(row: ResourceRow): void {
    for (const image of row.images ?? []) {
      this.api.imageBytes(image.filePath).subscribe({
        next: (blob) =>
          this.thumbnails.update((all) =>
            new Map(all).set(image.imageId, URL.createObjectURL(blob)),
          ),
        // a version deleted after the row was written leaves the path pointing at nothing, which
        // is a normal state rather than an error worth a message
        error: () =>
          this.missingThumbnails.update((all) => new Set(all).add(image.imageId)),
      });
    }
  }

  /** An object url the page has stopped drawing is a leak until it is revoked. */
  private releaseThumbnails(): void {
    for (const url of this.thumbnails().values()) {
      URL.revokeObjectURL(url);
    }
    this.thumbnails.set(new Map());
    this.missingThumbnails.set(new Set());
  }
}

/** 23:59:59.999 of the given day, so a range that names a day includes all of it. */
function endOfDay(date: Date): Date {
  const end = new Date(date);
  end.setHours(23, 59, 59, 999);
  return end;
}
