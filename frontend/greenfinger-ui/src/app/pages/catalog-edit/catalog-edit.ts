import { Component, inject, input, signal } from '@angular/core';
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
import { COUNTING_TYPES, Catalog, EXTRACTORS, OutputType } from '../../core/api.models';
import { NotifyService } from '../../core/notify.service';

/**
 * Creating and editing a crawl task.
 *
 * Everything but the url is optional, and the form says so rather than pre-filling guesses: the
 * server fills in a name, a start url, a path pattern and every limit from one url, and showing
 * invented values here would make the operator responsible for numbers they never chose. The
 * advanced sections start collapsed for the same reason -- a first catalog is a url and a Save.
 */
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
  protected readonly extractors = EXTRACTORS;

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal(false);
  protected readonly outputs = signal<OutputType[]>(['file']);

  protected readonly form = this.formBuilder.group({
    id: this.formBuilder.control<string | null>(null),
    url: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/i)]],
    name: [''],
    cat: [''],
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

  constructor() {
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
          cat: catalog.cat ?? '',
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
        this.router.navigate(['/catalogs']);
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
