import { DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { Catalog, SearchResponse, VectorHit } from '../../core/api.models';
import { NotifyService } from '../../core/notify.service';

type Mode = 'words' | 'meaning' | 'pictures';

/** Matches the server's own cap; past this a different query beats a deeper page. */
const MAX_VECTOR_OFFSET = 1000;

/**
 * Searching what was crawled, three ways.
 *
 * The three are genuinely different queries against different stores, not one query with a filter:
 * words go to Elasticsearch, meaning to the text vectors, pictures to the image vectors through a
 * model that puts words and images in one space. Presenting them as one box with a mode switch is
 * what makes that a choice the operator can make rather than a decision buried in the backend.
 *
 * Paging is by cursor, not by page number. Elasticsearch refuses from+size beyond ten thousand,
 * and the cursor keeps the cost of the ten-thousandth page the same as the first.
 */
@Component({
  selector: 'gf-search',
  imports: [
    FormsModule,
    DecimalPipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './search.html',
  styleUrl: './search.scss',
})
export class SearchPage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  protected readonly mode = signal<Mode>('words');
  protected readonly keyword = signal('');
  protected readonly catalog = signal('');
  protected readonly catalogs = signal<Catalog[]>([]);

  /**
   * Whether anything on this installation is indexed at all.
   *
   * Without it "nothing matched" is the only thing an empty result can say, and it blames the
   * query for a catalog that was never asked to write an index. The file output is always on and
   * the index one is not, so a perfectly successful crawl can leave search with nothing to read.
   */
  protected readonly nothingIndexed = computed(
    () =>
      this.catalogs().length > 0 &&
      !this.catalogs().some(
        (catalog) =>
          (catalog.searchVersion ?? -1) >= 0 && (catalog.outputTypes ?? []).includes('index'),
      ),
  );

  protected readonly searching = signal(false);
  protected readonly searched = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly response = signal<SearchResponse | null>(null);
  protected readonly hits = signal<VectorHit[]>([]);

  /** One cursor per page already seen, so Back is as cheap as Next. */
  private readonly cursors = signal<(unknown[] | null)[]>([null]);
  protected readonly pageIndex = signal(0);

  /**
   * Whether another vector page is worth offering. A full page means there is probably more; a
   * short one means the store has run out. There is no total to compare against -- a vector search
   * answers "the nearest n", and the number of things that exist is not one of its outputs.
   */
  protected readonly moreHits = signal(false);

  protected readonly modes: { value: Mode; label: string; icon: string; hint: string }[] = [
    {
      value: 'words',
      label: 'Words',
      icon: 'manage_search',
      hint: 'Elasticsearch. Exact terms, highlighted in context.',
    },
    {
      value: 'meaning',
      label: 'Meaning',
      icon: 'auto_awesome',
      hint: 'Finds the page that answers the question, even in other words.',
    },
    {
      value: 'pictures',
      label: 'Pictures',
      icon: 'image_search',
      hint: 'Describe what is in a picture — a colour, a subject. Not the text printed on it.',
    },
  ];

  constructor() {
    inject(DestroyRef).onDestroy(() => this.releasePictures());
    this.api.listCatalogs().subscribe({
      next: (catalogs) => this.catalogs.set(catalogs),
      error: () => undefined,
    });
  }

  protected setMode(mode: Mode): void {
    if (this.mode() === mode) {
      return;
    }
    this.mode.set(mode);
    this.reset();
    if (this.keyword().trim()) {
      this.search();
    }
  }

  protected search(): void {
    const keyword = this.keyword().trim();
    if (!keyword) {
      return;
    }
    this.reset();
    this.run(keyword, null);
  }

  protected nextPage(): void {
    if (this.mode() !== 'words') {
      if (!this.moreHits()) {
        return;
      }
      this.pageIndex.update((index) => index + 1);
      this.run(this.keyword().trim(), null);
      return;
    }
    const cursor = this.response()?.nextCursor ?? null;
    if (!cursor) {
      return;
    }
    this.cursors.update((all) => [...all.slice(0, this.pageIndex() + 1), cursor]);
    this.pageIndex.update((index) => index + 1);
    this.run(this.keyword().trim(), cursor);
  }

  protected previousPage(): void {
    if (this.pageIndex() === 0) {
      return;
    }
    this.pageIndex.update((index) => index - 1);
    this.run(
      this.keyword().trim(),
      this.mode() === 'words' ? (this.cursors()[this.pageIndex()] ?? null) : null,
    );
  }

  /** Twelve articles is a screenful; pictures are small, so two dozen is. */
  protected pageSize(): number {
    return this.mode() === 'meaning' ? 12 : 24;
  }

  private run(keyword: string, cursor: unknown[] | null): void {
    this.searching.set(true);
    this.error.set(null);
    const catalog = this.catalog() || undefined;

    if (this.mode() === 'words') {
      this.api.search({ q: keyword, catalog, size: 10, cursor }).subscribe({
        next: (response) => {
          this.response.set(response);
          this.finish();
        },
        error: (failure) => this.fail(failure),
      });
      return;
    }

    const size = this.pageSize();
    const offset = this.pageIndex() * size;
    const call =
      this.mode() === 'meaning'
        ? this.api.semanticSearch(keyword, catalog, size, offset)
        : this.api.imageSearch(keyword, catalog, size, offset);
    call.subscribe({
      next: (hits) => {
        this.hits.set(hits);
        if (this.mode() === 'pictures') {
          this.loadPictures(hits);
        }
        // the server refuses to page past a thousand, so stop offering it before it says no
        this.moreHits.set(hits.length === size && offset + size < MAX_VECTOR_OFFSET);
        this.finish();
      },
      error: (failure) => this.fail(failure),
    });
  }

  private finish(): void {
    this.searching.set(false);
    this.searched.set(true);
  }

  private fail(failure: unknown): void {
    this.searching.set(false);
    this.searched.set(true);
    this.response.set(null);
    this.hits.set([]);
    this.moreHits.set(false);
    // shown on the page rather than in a snackbar: "nothing has finished crawling yet" is an
    // answer to the search, not an incident
    this.error.set(this.notify.messageOf(failure, 'The search could not be run'));
  }

  private reset(): void {
    this.releasePictures();
    this.response.set(null);
    this.hits.set([]);
    this.moreHits.set(false);
    this.cursors.set([null]);
    this.pageIndex.set(0);
    this.searched.set(false);
    this.error.set(null);
  }

  /** Paths whose bytes the server could not produce; drawn as a placeholder instead of a broken box. */
  private readonly missing = signal(new Set<string>());

  /** One object url per hit, alive only while its tile is on screen. */
  private readonly pictures = signal(new Map<string, string>());

  /**
   * The tiles cannot be `<img src="/v2/image?path=...">`: that endpoint wants a bearer token and a
   * tag sends none, so every picture would come back 401 and draw as a broken box. The bytes are
   * fetched instead, through the interceptor that holds the token, and handed to the tag as an
   * object url.
   */
  private loadPictures(hits: VectorHit[]): void {
    this.releasePictures();
    for (const hit of hits) {
      const path = this.text(hit, 'imageFilePath');
      if (!path) {
        continue;
      }
      this.api.imageBytes(path).subscribe({
        next: (blob) =>
          this.pictures.update((all) => new Map(all).set(hit.id, URL.createObjectURL(blob))),
        // a version deleted after it was indexed leaves vectors pointing at files that are gone;
        // a normal state, not an error worth a message
        error: () => this.missing.update((all) => new Set(all).add(hit.id)),
      });
    }
  }

  /** An object url the page has stopped drawing is a leak until it is revoked. */
  private releasePictures(): void {
    for (const url of this.pictures().values()) {
      URL.revokeObjectURL(url);
    }
    this.pictures.set(new Map());
    this.missing.set(new Set());
  }

  protected imageUrl(hit: VectorHit): string {
    return this.pictures().get(hit.id) ?? '';
  }

  protected isMissing(hit: VectorHit): boolean {
    return this.missing().has(hit.id);
  }

  /** The bytes arrived but the browser could not decode them: the same dead end as a missing file. */
  protected onImageFailed(hit: VectorHit): void {
    this.missing.update((all) => new Set(all).add(hit.id));
  }

  /** Vector payload fields, read defensively: the store returns whatever was written into it. */
  protected text(hit: VectorHit, key: string): string {
    const value = hit.payload?.[key];
    return typeof value === 'string' ? value : value != null ? String(value) : '';
  }
}
