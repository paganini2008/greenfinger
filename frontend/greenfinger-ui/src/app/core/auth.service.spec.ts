import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

/**
 * The session. What is asserted here is the part that is easy to get wrong: a token in local
 * storage is a claim, not a fact, and only /me settles it.
 */
describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the token and the roles a successful login returned', () => {
    auth.login('admin', 'admin123').subscribe();

    http.expectOne('/v2/login').flush({
      success: true,
      message: 'ok',
      data: { token: 'abc', username: 'admin', roles: ['ROLE_ADMIN'], expiresInSeconds: 60 },
    });

    expect(auth.signedIn()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.token).toBe('abc');
    expect(localStorage.getItem('greenfinger.token')).toBe('abc');
  });

  it('signs support in without admin', () => {
    auth.login('tester', 'tester123').subscribe();

    http.expectOne('/v2/login').flush({
      success: true,
      message: 'ok',
      data: { token: 'xyz', username: 'tester', roles: ['ROLE_SUPPORT'], expiresInSeconds: 60 },
    });

    expect(auth.signedIn()).toBe(true);
    expect(auth.isAdmin()).toBe(false);
  });

  it('asks nobody when there is no stored token', () => {
    auth.restore().subscribe();

    http.expectNone('/v2/me');
    expect(auth.restored()).toBe(true);
    expect(auth.signedIn()).toBe(false);
  });

  it('believes a stored token only after /me confirms it', () => {
    localStorage.setItem('greenfinger.token', 'stored');

    auth.restore().subscribe();
    http.expectOne('/v2/me').flush({
      success: true,
      message: 'ok',
      data: { token: null, username: 'admin', roles: ['ROLE_ADMIN'], expiresInSeconds: 60 },
    });

    expect(auth.signedIn()).toBe(true);
    // /me does not repeat the token, so the stored one has to be carried over
    expect(auth.session()?.token).toBe('stored');
  });

  it('throws a revoked token away rather than showing a signed-in shell', () => {
    localStorage.setItem('greenfinger.token', 'revoked');

    auth.restore().subscribe();
    http.expectOne('/v2/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.signedIn()).toBe(false);
    expect(localStorage.getItem('greenfinger.token')).toBeNull();
    expect(auth.restored()).toBe(true);
  });

  it('signs out locally even when the logout call fails', () => {
    localStorage.setItem('greenfinger.token', 'abc');

    auth.logout().subscribe();
    http.expectOne('/v2/logout').flush({}, { status: 500, statusText: 'Server Error' });

    expect(auth.signedIn()).toBe(false);
    expect(localStorage.getItem('greenfinger.token')).toBeNull();
  });
});
