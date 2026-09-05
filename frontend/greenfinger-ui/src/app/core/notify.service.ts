import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * One place that decides how the operator is told something happened.
 *
 * The failure path is the part worth having: the backend answers in one envelope, so its message
 * is almost always the right thing to show, and falling back to "something went wrong" when a
 * server took the trouble to explain itself would be throwing away the explanation.
 */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);

  ok(message: string): void {
    this.snackBar.open(message, 'Close', { duration: 4000, panelClass: 'gf-snack-ok' });
  }

  /** Failures stay until dismissed: one that vanished after four seconds was never read. */
  failed(error: unknown, fallback = 'Something went wrong'): void {
    this.snackBar.open(this.messageOf(error, fallback), 'Close', { panelClass: 'gf-snack-failed' });
  }

  messageOf(error: unknown, fallback = 'Something went wrong'): string {
    if (error instanceof HttpErrorResponse) {
      // the api's own envelope, which says what the server actually objected to
      const body = error.error as { message?: string } | null;
      if (body?.message) {
        return body.message;
      }
      if (error.status === 0) {
        return 'The server did not answer. Is greenfinger-api.sh running?';
      }
      return `${error.status} ${error.statusText}`;
    }
    if (error instanceof Error && error.message) {
      return error.message;
    }
    return fallback;
  }
}
