import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { filter, interval, map, startWith, switchMap } from 'rxjs';
import { ApiService } from './core/api.service';
import { CrawlStatus } from './core/api.models';
import { AuthService } from './core/auth.service';

/** Below this the rail is an overlay. It is Material's own handset breakpoint. */
const NARROW = '(max-width: 599px)';

/**
 * The query, or nothing where there is no browser to ask.
 *
 * Guarded because {@code matchMedia} is not everywhere: the test environment has a window without
 * it, and a shell that cannot be rendered under test is a shell nobody can cover.
 */
function narrowQuery(): MediaQueryList | null {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(NARROW)
    : null;
}

function matchesNarrow(): boolean {
  return narrowQuery()?.matches ?? false;
}

/**
 * The frame every page sits in: the rail, and who is signed in. The login page is outside it --
 * a form inside a shell offering Catalogs invites clicks that only bounce back to it.
 */
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    MatTooltipModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  /**
   * A phone is not a small desktop: open at every width the rail took a third of a 430px screen
   * and pushed the page off the edge. Narrow, it is an overlay that starts closed.
   */
  private readonly narrow = signal(matchesNarrow());

  protected readonly sidenavMode = computed<'over' | 'side'>(() =>
    this.narrow() ? 'over' : 'side',
  );

  private readonly sidenavOpen = signal(!matchesNarrow());

  /** Open, except that a fresh overlay starts closed rather than covering the page. */
  protected readonly sidenavOpened = computed(() => this.sidenavOpen());

  /**
   * What is crawling anywhere in the cluster. In the shell because it is true of the whole
   * application: one crawl at a time across every node, which is why a button elsewhere will not
   * respond. Polled slowly -- it is a banner, not a progress bar.
   */
  protected readonly running = signal<CrawlStatus[]>([]);

  protected readonly runningName = computed(() => this.running()[0]?.name ?? '');

  /** Asked of the server rather than written here, so the badge cannot outlive the build it names. */
  protected readonly version = signal('');

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** The shell is for signed-in work; the login page stands on its own. */
  protected readonly showShell = computed(() => this.auth.signedIn() && !this.url().startsWith('/login'));

  protected readonly initial = computed(() => (this.auth.username()[0] ?? '?').toUpperCase());

  protected readonly role = computed(() => (this.auth.isAdmin() ? 'Administrator' : 'Support'));

  constructor() {
    // only while there is a shell to put it in: a signed-out visitor gets no requests at all
    interval(5000)
      .pipe(
        startWith(0),
        filter(() => this.auth.signedIn()),
        switchMap(() => this.api.status()),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (statuses) => this.running.set(statuses.filter((status) => status.running)),
        // a banner that cannot be drawn is not worth a message; the pages report what matters
        error: () => this.running.set([]),
      });

    // The width can change without a reload -- a rotated phone, a dragged window -- and the rail
    // has to follow it. Opening or closing on a change rather than only at startup is what keeps
    // a window dragged narrow from keeping a rail that no longer fits.
    const query = narrowQuery();
    if (query) {
      const onChange = (event: MediaQueryListEvent) => {
        this.narrow.set(event.matches);
        this.sidenavOpen.set(!event.matches);
      };
      query.addEventListener('change', onChange);
      this.destroyRef.onDestroy(() => query.removeEventListener('change', onChange));
    }

    this.api.version().subscribe({
      next: (server) => this.version.set(server.version),
      // a badge is not worth a message; the pages will report anything that actually matters
      error: () => undefined,
    });
  }

  /** The icon names what you would get by pressing it, which is how a three-way toggle reads. */

  protected toggleSidenav(): void {
    this.sidenavOpen.update((open) => !open);
  }

  /**
   * Following a link closes the overlay, because it is covering what was just asked for. On a
   * wide screen the rail is beside the page rather than over it, so it stays as it was.
   */
  protected navigated(): void {
    if (this.narrow()) {
      this.sidenavOpen.set(false);
    }
  }

  protected signOut(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
