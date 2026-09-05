/**
 * Turns the supplied logo into the files the site and the browser actually need.
 *
 * Two problems with the source, and this script exists to solve both:
 *
 *   - It has no transparency. The checkerboard in it is painted on, so dropping the file straight
 *     onto the green toolbar would show a white rectangle. The near-white pixels are keyed out
 *     here, with a soft edge so the letterforms keep their antialiasing.
 *   - It is a 3:1 wordmark, which at 16 pixels is a smear. The icon is its left mark alone -- the
 *     leafed G -- which is the part that stays recognisable in a crowded tab strip.
 *
 * Everything is derived from the one source file, so replacing the logo and running this again
 * cannot leave a stale icon behind.
 *
 *     npm run icons
 */
import { writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import pngToIco from 'png-to-ico';
import sharp from 'sharp';

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, '..', 'public');

/** Below this distance from white a pixel is background; above it, ink. The gap is the soft edge. */
const BACKGROUND = 8;
const INK = 28;

const source = sharp(join(publicDir, 'DefaultLogo.png'));
const { width, height } = await source.metadata();
const { data: rgb } = await source.clone().removeAlpha().raw().toBuffer({ resolveWithObject: true });

// How far a pixel is from white. Green ink is far, the painted checkerboard is a few units away,
// and paper white is zero -- which is what makes one threshold enough for all three.
const alpha = Buffer.alloc(width * height);
for (let i = 0, p = 0; i < rgb.length; i += 3, p++) {
  const distance = 255 - Math.min(rgb[i], rgb[i + 1], rgb[i + 2]);
  alpha[p] =
    distance <= BACKGROUND
      ? 0
      : distance >= INK
        ? 255
        : Math.round(((distance - BACKGROUND) * 255) / (INK - BACKGROUND));
}

const rgba = Buffer.alloc(width * height * 4);
for (let p = 0; p < width * height; p++) {
  rgba[p * 4] = rgb[p * 3];
  rgba[p * 4 + 1] = rgb[p * 3 + 1];
  rgba[p * 4 + 2] = rgb[p * 3 + 2];
  rgba[p * 4 + 3] = alpha[p];
}
const transparent = await sharp(rgba, { raw: { width, height, channels: 4 } }).png().toBuffer();

// The wordmark the site uses: trimmed to its own ink so no page has to guess at its margins, and
// resized down -- the login page shows it at about 270 css pixels, so 900 covers a 3x screen and
// saves most of the source file's weight on every page load.
const logo = await sharp(transparent)
  .trim({ threshold: 10 })
  .resize({ width: 900, withoutEnlargement: true })
  .png({ compressionLevel: 9 })
  .toBuffer();
await writeFile(join(publicDir, 'logo.png'), logo);

// Where the mark ends is found, not guessed: it and the first letter of the word are about forty
// pixels apart in a 2172 pixel image, and a fraction picked by eye clipped the leaf or let the
// "g" in. Scan for the first empty column after the ink has started.
const columnHasInk = (x) => {
  for (let y = 0; y < height; y++) {
    if (alpha[y * width + x] > 24) {
      return true;
    }
  }
  return false;
};

let cut = width;
let started = false;
for (let x = 0; x < width; x++) {
  if (columnHasInk(x)) {
    started = true;
  } else if (started) {
    cut = x;
    break;
  }
}

// Two pipelines rather than one: sharp applies trim before extract when they are chained, which
// would trim the whole wordmark and then cut a slice out of that.
const markOnly = await sharp(transparent)
  .extract({ left: 0, top: 0, width: cut, height })
  .png()
  .toBuffer();
const trimmed = await sharp(markOnly).trim({ threshold: 10 }).png().toBuffer();
const { width: markWidth, height: markHeight } = await sharp(trimmed).metadata();

// Squared on the longer side, with a little air, so rounding of the tab icon cannot clip the leaf.
const side = Math.round(Math.max(markWidth, markHeight) * 1.12);
const square = await sharp({
  create: { width: side, height: side, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
})
  .composite([
    {
      input: trimmed,
      left: Math.round((side - markWidth) / 2),
      top: Math.round((side - markHeight) / 2),
    },
  ])
  .png()
  .toBuffer();

await writeFile(join(publicDir, 'mark.png'), square);

const at = async (size) => sharp(square).resize(size, size).png().toBuffer();
await writeFile(join(publicDir, 'icon-512.png'), await at(512));
await writeFile(join(publicDir, 'icon-192.png'), await at(192));
// iOS composites a transparent icon onto black, so that one is given a white ground of its own
await writeFile(
  join(publicDir, 'apple-touch-icon.png'),
  await sharp(square).resize(180, 180).flatten({ background: '#ffffff' }).png().toBuffer(),
);
// Three sizes in the one .ico, so the browser picks rather than scales
await writeFile(
  join(publicDir, 'favicon.ico'),
  await pngToIco([await at(64), await at(32), await at(16)]),
);

console.log(
  `logo keyed to transparency; mark cut at x=${cut} of ${width} (${markWidth}x${markHeight}), icons at 512/192/180/64/32/16`,
);
