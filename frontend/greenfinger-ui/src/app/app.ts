import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';
import { ThemeService } from './core/theme.service';

/**
 * The frame every page sits in: a green bar, a nav rail, and who is signed in.
 *
 * The login page is deliberately outside it. A form that appears inside a shell offering Catalogs
 * and Search invites the visitor to click things that will only bounce them back here, so while
 * signed out there is nothing but the form.
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
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);

  protected readonly sidenavOpen = signal(true);

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
    this.api.version().subscribe({
      next: (server) => this.version.set(server.version),
      // a badge is not worth a message; the pages will report anything that actually matters
      error: () => undefined,
    });
  }

  /** The icon names what you would get by pressing it, which is how a three-way toggle reads. */
  protected readonly themeIcon = computed(() => {
    switch (this.theme.choice()) {
      case 'system':
        return 'light_mode';
      case 'light':
        return 'dark_mode';
      default:
        return 'brightness_auto';
    }
  });

  protected readonly themeLabel = computed(() => {
    switch (this.theme.choice()) {
      case 'system':
        return 'Following the system; switch to light';
      case 'light':
        return 'Light; switch to dark';
      default:
        return 'Dark; follow the system';
    }
  });

  protected toggleSidenav(): void {
    this.sidenavOpen.update((open) => !open);
  }

  protected signOut(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
