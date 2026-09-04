import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { API_PREFIX } from './api.config';
import { ApiResult, Session } from './api.models';

const TOKEN_KEY = 'greenfinger.token';

/**
 * Who is signed in.
 *
 * The token is kept in local storage so a page refresh does not sign the operator out in the
 * middle of watching a crawl. What is *not* trusted is the rest of the session: on every start the
 * token is sent to /me and the answer decides, because a token can have been revoked -- by a
 * logout elsewhere, or by a server restart -- since it was written down. Believing local storage
 * would show a signed-in shell whose every call then failed.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _session = signal<Session | null>(null);
  /** Undefined until /me has answered, so the router can wait rather than guess. */
  private readonly _restored = signal(false);

  readonly session = this._session.asReadonly();
  readonly restored = this._restored.asReadonly();
  readonly username = computed(() => this._session()?.username ?? '');
  readonly signedIn = computed(() => this._session() !== null);
  /** Drives what the pages offer: SUPPORT sees no button it would only be refused on. */
  readonly isAdmin = computed(() => this._session()?.roles.includes('ROLE_ADMIN') ?? false);

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  login(username: string, password: string): Observable<ApiResult<Session>> {
    return this.http
      .post<ApiResult<Session>>(`${API_PREFIX}/login`, { username, password })
      .pipe(tap((result) => this.accept(result.data)));
  }

  /**
   * Clears locally whatever the server says. A logout that failed because the network is down must
   * still leave the browser signed out -- refusing to would be the one failure mode nobody expects.
   */
  logout(): Observable<unknown> {
    return this.http.post(`${API_PREFIX}/logout`, {}).pipe(
      catchError(() => of(null)),
      tap(() => this.forget()),
    );
  }

  /** Called once at startup: asks the server whether the stored token is still anybody's. */
  restore(): Observable<Session | null> {
    if (!this.token) {
      this._restored.set(true);
      return of(null);
    }
    return this.http.get<ApiResult<Session>>(`${API_PREFIX}/me`).pipe(
      map((result) => {
        // /me does not repeat the token -- the caller sent it -- so it is carried over here
        const session: Session = { ...result.data, token: this.token! };
        this.accept(session);
        return session;
      }),
      catchError(() => {
        this.forget();
        return of(null);
      }),
      tap(() => this._restored.set(true)),
    );
  }

  /** Called by the interceptor when the server answers 401: the token died under us. */
  forget(): void {
    localStorage.removeItem(TOKEN_KEY);
    this._session.set(null);
  }

  private accept(session: Session): void {
    if (session?.token) {
      localStorage.setItem(TOKEN_KEY, session.token);
    }
    this._session.set(session);
  }
}
