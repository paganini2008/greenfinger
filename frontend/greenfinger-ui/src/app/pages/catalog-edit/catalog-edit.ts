import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { COUNTING_TYPES, Catalog, EXTRACTORS, OutputStores, OutputType } from '../../core/api.models';
import { NotifyService } from '../../core/notify.service';

/**
 * Creating and editing a crawl task. Everything but the url is optional and stays empty rather
 * than pre-filled: the server derives the rest, and a first catalog is a url and a Save.
 */
/**
 * The provider names as somebody would say them. Anything unknown is shown as it came: a store
 * this page has not heard of is still better named by the server than by a guess here.
 */
const STORE_NAMES: Record<string, string> = {
  local: 'A local directory',
  minio: 'MinIO',
  lucene: 'Lucene, in this process',
  elasticsearch: 'Elasticsearch',
  qdrant: 'Qdrant',
  weaviate: 'Weaviate',
};

function storeName(value: string): string {
  return STORE_NAMES[(value ?? '').toLowerCase()] ?? value;
}

@Component({
  selector: 'gf-catalog-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatExpansionModule,
    MatTooltipModule,
    MatProgressBarModule,
  ],
  templateUrl: './catalog-edit.html',
  styleUrl: './catalog-edit.scss',
})
export class CatalogEditPage {
  /** Bound from the route: absent when creating. */
  readonly ref = input<string>();

  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);

  protected readonly countingTypes = COUNTING_TYPES;

  /**
   * The chosen option's own explanation, under the field.
   *
   * The dropdown is closed most of the time, and the whole point of the hint is that "Urls seen"
   * and "Pages saved" mean very different things for the number in the box above.
   */
  protected readonly extractors = EXTRACTORS;

  /**
   * Asked of the server: a list written here goes stale the first time one is added, silently.
   * Falls back to the one safe value if the call fails.
   */
  protected readonly categories = signal<string[]>(['other']);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal(false);
  protected readonly outputs = signal<OutputType[]>(['file']);

  /**
   * What each output card says. Asked of the server -- written here, an installation on the
   * embedded index was told it was Elasticsearch. The defaults are what a fresh install runs.
   */
  protected readonly stores = signal<OutputStores>({
    file: 'local',
    index: 'lucene',
    vector: 'lucene',
  });

  protected readonly indexNote = computed(() => `${storeName(this.stores().index)}. Search by words.`);

  protected readonly vectorNote = computed(
    () => `${storeName(this.stores().vector)}. Meaning and pictures.`,
  );

  protected readonly fileNote = computed(
    () => `${storeName(this.stores().file)}. html, text and images. Always on.`,
  );

  protected readonly form = this.formBuilder.group({
    id: this.formBuilder.control<string | null>(null),
    url: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/i)]],
    name: [''],
    cat: ['other'],
    startUrl: [''],
    sitemapUrl: [''],
    pathPattern: [''],
    excludedPathPattern: [''],
    urlPathAcceptor: [''],
    pageEncoding: [''],
    extractor: ['adaptive'],
    maxFetchSize: this.formBuilder.control<number | null>(null),
    depth: this.formBuilder.control<number | null>(null),
    duration: this.formBuilder.control<number | null>(null),
    fetchInterval: this.formBuilder.control<number | null>(null),
    maxRetryCount: this.formBuilder.control<number | null>(null),
    countingType: this.formBuilder.control<number | null>(null),
    maxVersions: this.formBuilder.control<number | null>(null),
    imageEnabled: [true],
    contentMode: ['text+image'],
  });

  /** The control's value as a signal, so the hint under the field follows the dropdown. */
  private readonly countingType = toSignal(this.form.controls.countingType.valueChanges, {
    initialValue: this.form.controls.countingType.value,
  });

  protected readonly countingHint = computed(() => {
    const chosen = this.countingType();
    const found = COUNTING_TYPES.find((type) => type.value === chosen);
    return found ? found.hint : 'What the limit above counts. Defaults to pages saved.';
  });

  constructor() {
    this.api.outputs().subscribe({
      next: (stores) => this.stores.set(stores),
      // the defaults stand; a card with the wrong word on it is not worth a red banner
      error: () => undefined,
    });
    this.api.listCategories().subscribe({
      next: (categories) => this.categories.set(categories.length ? categories : ['other']),
      error: () => undefined,
    });
    // input() is set before the first change detection, so reading it here is safe and saves
    // wiring an effect for a value that never changes over the page's life
    queueMicrotask(() => {
      const ref = this.ref();
      if (ref) {
        this.load(ref);
      }
    });
  }

  private load(ref: string): void {
    this.loading.set(true);
    this.editing.set(true);
    this.api.getCatalog(ref).subscribe({
      next: (catalog) => {
        this.form.patchValue({
          id: catalog.id ?? null,
          url: catalog.url,
          name: catalog.name ?? '',
          cat: catalog.cat ?? 'other',
          startUrl: catalog.startUrl ?? '',
          sitemapUrl: catalog.sitemapUrl ?? '',
          pathPattern: catalog.pathPattern ?? '',
          excludedPathPattern: catalog.excludedPathPattern ?? '',
          urlPathAcceptor: catalog.urlPathAcceptor ?? '',
          pageEncoding: catalog.pageEncoding ?? '',
          extractor: catalog.extractor ?? 'adaptive',
          maxFetchSize: catalog.maxFetchSize ?? null,
          depth: catalog.depth ?? null,
          duration: catalog.duration ?? null,
          fetchInterval: catalog.fetchInterval ?? null,
          maxRetryCount: catalog.maxRetryCount ?? null,
          countingType: catalog.countingType ?? null,
          maxVersions: catalog.maxVersions ?? null,
          imageEnabled: catalog.imageEnabled ?? true,
          contentMode: catalog.contentMode ?? 'text+image',
        });
        this.outputs.set(catalog.outputTypes?.length ? catalog.outputTypes : ['file']);
        this.loading.set(false);
      },
      error: (failure) => {
        this.loading.set(false);
        this.notify.failed(failure, 'Could not load that catalog');
        this.router.navigate(['/catalogs']);
      },
    });
  }

  /** file is not a choice: without the files there is nothing for the other two to be rebuilt from. */
  protected toggleOutput(output: OutputType): void {
    if (output === 'file') {
      return;
    }
    this.outputs.update((current) =>
      current.includes(output) ? current.filter((one) => one !== output) : [...current, output],
    );
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.api.saveCatalog(this.payload()).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notify.ok(`'${saved.name}' saved`);
        // The id and the write stamp travel to the list, which may be served by a node that has
        // not applied this write yet. Presence alone is true of the stale copy; the stamp is not.
        this.router.navigate(['/catalogs'], {
          queryParams: {
            saved: saved.id ?? saved.name,
            at: saved.updatedAt ? Date.parse(saved.updatedAt) : null,
          },
        });
      },
      error: (failure) => {
        this.saving.set(false);
        this.notify.failed(failure);
      },
    });
  }

  /**
   * Blank means "you decide", so blanks are dropped rather than sent as empty strings. The server
   * fills in a default for every field it does not receive, and an empty string is not a default --
   * it is an instruction to use nothing.
   */
  private payload(): Catalog {
    const raw = this.form.getRawValue();
    const catalog: Record<string, unknown> = { outputTypes: this.outputs() };
    for (const [key, value] of Object.entries(raw)) {
      if (value === null || value === '') {
        continue;
      }
      catalog[key] = value;
    }
    // a checkbox is never blank, so it is always sent, and never guessed at
    catalog['imageEnabled'] = raw.imageEnabled;
    return catalog as unknown as Catalog;
  }
}
