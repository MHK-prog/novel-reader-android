#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const args = process.argv.slice(2);
const apply = args.includes('--apply');
const input = args.find((arg) => !arg.startsWith('--')) ?? 'library.json';
const file = path.resolve(input);

if (!fs.existsSync(file)) {
  console.error(`Library file not found: ${file}`);
  process.exit(1);
}

const library = JSON.parse(fs.readFileSync(file, 'utf8'));
if (!Array.isArray(library.genres) || !Array.isArray(library.books)) {
  console.error('Expected library.json with "genres" and "books" arrays.');
  process.exit(1);
}

const key = (value) => String(value ?? '').trim().toLocaleLowerCase('en-US');
const allGenres = [
  ...library.genres,
  ...library.books.flatMap((book) => Array.isArray(book?.genres) ? book.genres : []),
];
const known = new Set(allGenres.map((value) => {
  const trimmed = String(value ?? '').trim();
  return trimmed && !/\s/.test(trimmed) ? key(trimmed) : '';
}).filter(Boolean));
const compounds = new Map();

for (const genre of allGenres) {
  const value = String(genre ?? '').trim();
  const parts = value.split(/\s+/).filter(Boolean);
  if (parts.length > 1 && parts.every((part) => known.has(key(part)))) {
    compounds.set(key(value), parts);
  }
}

let bookChanges = 0;
for (const book of library.books) {
  if (!Array.isArray(book?.genres)) continue;
  const rebuilt = [];
  const seen = new Set();
  for (const genre of book.genres) {
    const replacements = compounds.get(key(genre)) ?? [genre];
    for (const replacement of replacements) {
      const normalized = key(replacement);
      if (normalized && !seen.has(normalized)) {
        seen.add(normalized);
        rebuilt.push(replacement);
      }
    }
  }
  if (JSON.stringify(rebuilt) !== JSON.stringify(book.genres)) {
    book.genres = rebuilt;
    bookChanges++;
  }
}

if (compounds.size) {
  library.genres = library.genres.filter((genre) => !compounds.has(key(genre)));
  if (Array.isArray(library.genreDetails)) {
    library.genreDetails = library.genreDetails.filter((detail) =>
      !compounds.has(key(detail?.name ?? detail?.title)));
  }
}

console.log(`Composite genres found: ${compounds.size}`);
for (const [composite, parts] of compounds) console.log(`  ${composite} -> ${parts.join(' + ')}`);
console.log(`Books changed: ${bookChanges}`);

if (!apply) {
  console.log('Dry run only. Add --apply to write changes and create a backup.');
  process.exit(0);
}

const backup = `${file}.before-genre-normalization.bak`;
if (fs.existsSync(backup)) {
  console.error(`Refusing to overwrite existing backup: ${backup}`);
  process.exit(1);
}
fs.copyFileSync(file, backup);
fs.writeFileSync(file, `${JSON.stringify(library, null, 2)}\n`, 'utf8');
console.log(`Updated: ${file}`);
console.log(`Backup:  ${backup}`);
