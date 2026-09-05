import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the bearer token, and reacts to the one answer no page can handle on its own.
 *
 * A 401 anywhere means the token is gone -- expired, or revoked by a logout in another tab -- so
 * the session is dropped and the browser sent to the login page. Doing it here rather than in each
 * page is the point: there is exactly one place that decides what a dead token means.
 *
 * A 403 is left alone. It is not a broken session but an answer: this account may read and not
 * write, and the page that asked should say so rather than throw the operator out.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token;

  const authorized = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      // the login call itself answers 401 for a wrong password; that is the form's to show
      const isLogin = request.url.endsWith('/login');
      if (error.status === 401 && !isLogin) {
        auth.forget();
        router.navigate(['/login'], { queryParams: { next: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
