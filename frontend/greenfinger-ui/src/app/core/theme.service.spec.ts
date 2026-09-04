import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

/**
 * The theme is one attribute on <html> plus one key in localStorage. What is worth pinning down is
 * that "system" means the attribute is absent -- if it were set to anything, the browser would stop
 * following the operating system, which is the one thing that choice has to do.
 */
describe('ThemeService', () => {
  let root: HTMLElement;

  beforeEach(() => {
    root = document.documentElement;
    delete root.dataset['theme'];
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    delete root.dataset['theme'];
    localStorage.clear();
  });

  it('follows the system until somebody chooses', () => {
    const theme = TestBed.inject(ThemeService);

    expect(theme.choice()).toBe('system');
    expect(root.dataset['theme']).toBeUndefined();
  });

  it('stamps the choice on the document and remembers it', () => {
    const theme = TestBed.inject(ThemeService);

    theme.set('dark');

    expect(root.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('gf-theme')).toBe('dark');
  });

  it('going back to system removes the attribute rather than setting it to something', () => {
    const theme = TestBed.inject(ThemeService);
    theme.set('dark');

    theme.set('system');

    expect(root.dataset['theme']).toBeUndefined();
    expect(localStorage.getItem('gf-theme')).toBe('system');
  });

  it('cycles system, light, dark and round again from one button', () => {
    const theme = TestBed.inject(ThemeService);

    theme.next();
    expect(theme.choice()).toBe('light');
    theme.next();
    expect(theme.choice()).toBe('dark');
    theme.next();
    expect(theme.choice()).toBe('system');
  });

  it('applies what was stored, so a reload does not flash the other theme', () => {
    localStorage.setItem('gf-theme', 'dark');

    const theme = TestBed.inject(ThemeService);

    expect(theme.choice()).toBe('dark');
    expect(root.dataset['theme']).toBe('dark');
  });

  it('ignores a stored value that is not a theme', () => {
    localStorage.setItem('gf-theme', 'chartreuse');

    expect(TestBed.inject(ThemeService).choice()).toBe('system');
  });
});
