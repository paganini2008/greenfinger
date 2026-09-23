import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';
import { App } from './app';
import { AuthService } from './core/auth.service';

/**
 * The shell decides one thing: whether there is a shell at all. Signed out, the login form must
 * stand alone -- a nav rail offering Catalogs to somebody who cannot open them is worse than no
 * nav rail.
 */
describe('App shell', () => {
  const noMatchMedia = Symbol('absent');
  let restoreMatchMedia: typeof window.matchMedia | typeof noMatchMedia = noMatchMedia;

  /** A window that answers the rail's question, and is put back afterwards. */
  function stubMatchMedia(matches: boolean): void {
    restoreMatchMedia = 'matchMedia' in window ? window.matchMedia : noMatchMedia;
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      // addListener/removeListener as well as the modern pair: Angular's own BreakpointObserver
      // still reaches for the deprecated ones, and a stub without them throws from inside the
      // sidenav rather than from anything this file wrote
      value: (media: string) => ({
        media,
        matches,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false,
      }),
    });
  }

  afterEach(() => {
    if (restoreMatchMedia === noMatchMedia) {
      delete (window as { matchMedia?: unknown }).matchMedia;
    } else {
      Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        writable: true,
        value: restoreMatchMedia,
      });
    }
    restoreMatchMedia = noMatchMedia;
  });

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows nothing but the outlet while signed out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('mat-toolbar')).toBeNull();
  });

  it('shows the version the server reports, rather than one written into the page', async () => {
    TestBed.inject(AuthService)['_session'].set({
      token: 't',
      username: 'admin',
      roles: ['ROLE_ADMIN'],
      expiresInSeconds: 60,
    });

    const fixture = TestBed.createComponent(App);
    TestBed.inject(HttpTestingController)
      .expectOne('/v2/version')
      .flush({ success: true, message: 'ok', data: { name: 'Greenfinger', version: '2.0.0' } });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2.0.0');
  });

  it('puts the toolbar and the nav up once somebody is signed in', async () => {
    const auth = TestBed.inject(AuthService);
    auth['_session'].set({
      token: 't',
      username: 'admin',
      roles: ['ROLE_ADMIN'],
      expiresInSeconds: 60,
    });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('mat-toolbar')).not.toBeNull();
    // the wordmark is an image, so the name is in its alt text and not in the page's text
    expect(element.querySelector('img.gf-logo')?.getAttribute('alt')).toBe('Greenfinger');
    expect(element.textContent).toContain('Catalogs');
  });

  /**
   * A phone gets an overlay, not a third of its screen. The environment has no matchMedia, which
   * is what the component guards against, so the query is supplied here.
   */
  it('puts the rail over the page, closed, on a narrow screen', async () => {
    stubMatchMedia(true);
    TestBed.inject(AuthService)['_session'].set({
      token: 't',
      username: 'admin',
      roles: ['ROLE_ADMIN'],
      expiresInSeconds: 60,
    });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const drawer = (fixture.nativeElement as HTMLElement).querySelector('mat-sidenav');

    expect(drawer?.classList.contains('mat-drawer-over')).toBe(true);
    expect(drawer?.classList.contains('mat-drawer-opened')).toBe(false);
  });

  it('leaves the rail beside the page, open, on a wide one', async () => {
    stubMatchMedia(false);
    TestBed.inject(AuthService)['_session'].set({
      token: 't',
      username: 'admin',
      roles: ['ROLE_ADMIN'],
      expiresInSeconds: 60,
    });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const drawer = (fixture.nativeElement as HTMLElement).querySelector('mat-sidenav');

    expect(drawer?.classList.contains('mat-drawer-side')).toBe(true);
    expect(drawer?.classList.contains('mat-drawer-opened')).toBe(true);
  });

  it('offers New catalog to an administrator and not to support', async () => {
    const auth = TestBed.inject(AuthService);
    auth['_session'].set({
      token: 't',
      username: 'tester',
      roles: ['ROLE_SUPPORT'],
      expiresInSeconds: 60,
    });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).not.toContain('New catalog');
    expect(element.textContent).toContain('Read only');
  });
});
