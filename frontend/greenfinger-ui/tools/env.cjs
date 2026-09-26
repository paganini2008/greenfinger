/**
 * The one reader of `.env`, so every part of this project's tooling sees the same settings.
 *
 * There is no dotenv here and there does not need to be: the file is `KEY=value`, optionally
 * `export KEY=value`, optionally quoted, with `#` comments -- the same shape the shell scripts in
 * `deploy/` read. Anything already in the environment wins, so a one-off stays a one-off:
 *
 *     GF_API_PORT=50081 npm start
 *
 * The file itself is not in the repository, like every other `.env` here. Everything that reads
 * it has a default for a fresh clone.
 */
const fs = require('node:fs');
const path = require('node:path');

const FILE = path.resolve(__dirname, '../.env');

function fromFile() {
  if (!fs.existsSync(FILE)) {
    return {};
  }
  const values = {};
  for (const line of fs.readFileSync(FILE, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (match && !line.trimStart().startsWith('#')) {
      values[match[1]] = match[2].replace(/^["']|["']$/g, '');
    }
  }
  return values;
}

const file = fromFile();

/** The value for a key: the environment first, then `.env`, then the default given here. */
function setting(key, fallback) {
  const fromEnvironment = process.env[key];
  const value = (fromEnvironment ?? file[key] ?? '').trim();
  return value === '' ? fallback : value;
}

module.exports = { setting, file, FILE };
