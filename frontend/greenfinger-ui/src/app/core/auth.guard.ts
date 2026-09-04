import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Closed to anyone not signed in.
 *
 * The check is against what /me confirmed at startup, not against the token sitting in local
 * storage: a revoked token must not be enough to render the shell. Where the visitor was going is
 * remembered so the login form can send them on rather than dropping them on the home page.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.signedIn() ? true : router.createUrlTree(['/login'], { queryParams: { next: state.url } });
};

/**
 * Closed to accounts that may only read.
 *
 * The server refuses these calls anyway -- this is not where the rule lives -- but a form that
 * cannot be submitted is worse than a page that was never offered.
 */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.signedIn()) {
    return router.createUrlTree(['/login'], { queryParams: { next: state.url } });
  }
  return auth.isAdmin() ? true : router.createUrlTree(['/catalogs']);
};
