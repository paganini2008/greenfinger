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
 * The pause before something irreversible: deleting a catalog, and deleting versions. Not for
 * starting a crawl, which interrupting undoes -- a confirmation asked for everything is unread.
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
        [class.gf-destructive]="data.destructive"
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

    /*
     * The one place red is allowed: Material ignores color="warn" on mat-flat-button, so the
     * button that empties a catalog looked like the one that saves it. Outlined rather than
     * filled -- a red slab is the loudest thing here, drawn when somebody is already careful.
     */
    .gf-destructive {
      --mdc-filled-button-container-color: transparent;
      --mdc-filled-button-label-text-color: var(--mat-sys-error);
      background: transparent;
      color: var(--mat-sys-error);
      box-shadow: inset 0 0 0 1px var(--mat-sys-error);
    }

    .gf-destructive:hover {
      background: color-mix(in srgb, var(--mat-sys-error) 8%, transparent);
    }
  `,
})
export class ConfirmDialog {
  protected readonly dialogRef = inject(MatDialogRef<ConfirmDialog, boolean>);
  protected readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
}
