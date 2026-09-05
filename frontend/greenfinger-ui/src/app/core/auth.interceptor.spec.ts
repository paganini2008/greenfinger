import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

/**
 * The one place that decides what a dead token means. A 401 has to end the session everywhere at
 * once; a 403 must not, because it is an answer rather than a broken session.
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => backend.verify());

  it('attaches the bearer token when there is one', () => {
    localStorage.setItem('greenfinger.token', 'abc');

    http.get('/v2/catalog').subscribe();

    const request = backend.expectOne('/v2/catalog');
    expect(request.request.headers.get('Authorization')).toBe('Bearer abc');
    request.flush({});
  });

  it('sends nothing when signed out, so the login call is not carrying a dead token', () => {
    http.post('/v2/login', {}).subscribe();

    const request = backend.expectOne('/v2/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('ends the session and returns to the login page on a 401', () => {
    localStorage.setItem('greenfinger.token', 'abc');
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    http.get('/v2/catalog').subscribe({ error: () => undefined });
    backend.expectOne('/v2/catalog').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(localStorage.getItem('greenfinger.token')).toBeNull();
    expect(auth.signedIn()).toBe(false);
    expect(navigate).toHaveBeenCalled();
  });

  it('leaves a wrong password on the login form rather than redirecting the page it is on', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    http.post('/v2/login', {}).subscribe({ error: () => undefined });
    backend.expectOne('/v2/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });

  it('keeps the session on a 403: support being refused a write is not a broken session', () => {
    localStorage.setItem('greenfinger.token', 'abc');

    http.post('/v2/catalog', {}).subscribe({ error: () => undefined });
    backend.expectOne('/v2/catalog').flush({}, { status: 403, statusText: 'Forbidden' });

    expect(localStorage.getItem('greenfinger.token')).toBe('abc');
  });
});
