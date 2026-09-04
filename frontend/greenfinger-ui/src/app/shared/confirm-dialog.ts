import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel?: string;
  /** Paints the confirm button as a warning. For anything that cannot be undone. */
  destructive?: boolean;
}

/**
 * The pause before something irreversible.
 *
 * Used for exactly two things -- deleting a catalog, and deleting crawled versions -- and not for
 * starting a crawl, which is undone by interrupting it. A confirmation asked for everything is a
 * confirmation nobody reads.
 */
@Component({
  selector: 'gf-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="flex items-center gap-2">
      @if (data.destructive) {
        <mat-icon class="gf-warn-icon">warning</mat-icon>
      }
      {{ data.title }}
    </h2>
    <mat-dialog-content class="whitespace-pre-line">{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close(false)">Cancel</button>
      <button
        mat-flat-button
        [color]="data.destructive ? 'warn' : 'primary'"
        (click)="dialogRef.close(true)"
      >
        {{ data.confirmLabel ?? 'Confirm' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .gf-warn-icon {
      color: var(--mat-sys-error);
    }
  `,
})
export class ConfirmDialog {
  protected readonly dialogRef = inject(MatDialogRef<ConfirmDialog, boolean>);
  protected readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
}
