import { Component, ChangeDetectionStrategy, computed, input } from '@angular/core';

/**
 * A line over a filled area, as inline svg. Hand drawn: every library that would do this is bigger
 * than the application, and what they add -- axes, legends, tooltips -- is what a sparkline is
 * defined by not having. The viewBox is fixed and stretched, so nothing here knows its width.
 */
@Component({
  selector: 'gf-sparkline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      class="gf-spark"
      [attr.viewBox]="'0 0 ' + W + ' ' + H"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <defs>
        <linearGradient [attr.id]="gradientId()" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" [attr.stop-color]="stroke()" stop-opacity="0.35" />
          <stop offset="100%" [attr.stop-color]="stroke()" stop-opacity="0" />
        </linearGradient>
      </defs>
      @if (area(); as area) {
        <path [attr.d]="area" [attr.fill]="'url(#' + gradientId() + ')'" />
      }
      @if (line(); as line) {
        <path
          [attr.d]="line"
          fill="none"
          [attr.stroke]="stroke()"
          stroke-width="2"
          stroke-linejoin="round"
          stroke-linecap="round"
          vector-effect="non-scaling-stroke"
        />
      }
      @if (head(); as head) {
        <circle [attr.cx]="head.x" [attr.cy]="head.y" r="2.5" [attr.fill]="stroke()" />
      }
    </svg>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
    }

    .gf-spark {
      display: block;
      width: 100%;
      height: 100%;
      overflow: visible;
    }
  `,
})
export class Sparkline {
  /** Oldest first. Fewer than two points draws nothing: one point is not a shape. */
  readonly values = input<number[]>([]);
  readonly stroke = input('var(--gf-green)');

  /**
   * The top of the scale. At 0 the chart scales to its own peak, which makes a flat line of 4s
   * and one of 400s identical; give it a number and the same height means the same value.
   */
  readonly max = input(0);

  protected readonly W = 100;
  protected readonly H = 32;

  /** Unique per instance: two gradients sharing an id on one page is one gradient. */
  protected readonly gradientId = computed(() => `gf-spark-${Sparkline.next++}`);
  private static next = 0;

  private readonly points = computed(() => {
    const values = this.values();
    if (values.length < 2) {
      return [];
    }
    const ceiling = Math.max(this.max(), ...values, 1);
    const step = this.W / (values.length - 1);
    // 1px of padding top and bottom, so a peak at the ceiling is not clipped by the stroke width
    return values.map((value, index) => ({
      x: index * step,
      y: this.H - 1 - (Math.max(0, value) / ceiling) * (this.H - 2),
    }));
  });

  protected readonly line = computed(() => {
    const points = this.points();
    return points.length
      ? points.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ')
      : '';
  });

  protected readonly area = computed(() => {
    const line = this.line();
    return line ? `${line} L${this.W} ${this.H} L0 ${this.H} Z` : '';
  });

  protected readonly head = computed(() => this.points().at(-1) ?? null);
}
