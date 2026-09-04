import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { NotifyService } from '../../core/notify.service';

/**
 * Signing in.
 *
 * The only page a signed-out visitor can reach, and the only one outside the shell. A failure is
 * shown in the form rather than in a snackbar: the thing that went wrong is on this page, and the
 * operator is about to retype it here.
 */
@Component({
  selector: 'gf-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notify = inject(NotifyService);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = this.formBuilder.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(username, password).subscribe({
      next: () => {
        this.busy.set(false);
        // back to wherever the guard turned them away from, or the catalog list
        const next = this.route.snapshot.queryParamMap.get('next');
        this.router.navigateByUrl(next && !next.startsWith('/login') ? next : '/catalogs');
      },
      error: (failure) => {
        this.busy.set(false);
        this.error.set(this.notify.messageOf(failure, 'Wrong username or password'));
      },
    });
  }
}
