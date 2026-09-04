import { Injectable, signal } from '@angular/core';

/** What the viewer picked, not what they are seeing: 'system' resolves at paint time. */
export type ThemeChoice = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'gf-theme';

/**
 * Light, dark, or whatever the machine says.
 *
 * The switch is one attribute on <html>; every colour in the app already comes from a
 * --mat-sys-* token, and those are generated as light-dark() pairs, so setting color-scheme is
 * the whole of the theming. Nothing here knows a single colour value.
 *
 * The choice is remembered per browser rather than per account: it is a property of the screen
 * somebody is sitting at, not of who they signed in as, and a shared operations machine should
 * not flip appearance when the shift changes.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _choice = signal<ThemeChoice>(ThemeService.stored());

  readonly choice = this._choice.asReadonly();

  constructor() {
    this.apply(this._choice());
  }

  set(choice: ThemeChoice): void {
    this._choice.set(choice);
    this.apply(choice);
    try {
      localStorage.setItem(STORAGE_KEY, choice);
    } catch {
      // private browsing, or storage turned off: the theme still applies for this session
    }
  }

  /** system -> light -> dark -> system, so one button covers all three. */
  next(): void {
    const order: ThemeChoice[] = ['system', 'light', 'dark'];
    this.set(order[(order.indexOf(this._choice()) + 1) % order.length]);
  }

  private apply(choice: ThemeChoice): void {
    const root = document.documentElement;
    if (choice === 'system') {
      // absent, rather than set to anything: html's own `color-scheme: light dark` then lets the
      // browser follow the operating system, which is what "system" has to mean
      delete root.dataset['theme'];
    } else {
      root.dataset['theme'] = choice;
    }
  }

  private static stored(): ThemeChoice {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'light' || saved === 'dark' || saved === 'system') {
        return saved;
      }
    } catch {
      // as above
    }
    return 'system';
  }
}
