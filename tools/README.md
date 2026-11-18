# One-time genre cleanup

The app now performs the same one-time cleanup when it first loads an older library into Room. This script is for a `library.json` backup or a file that has not been imported yet:

```powershell
node tools/normalize-compound-genres.mjs "C:\path\to\NovelReader\library.json"
node tools/normalize-compound-genres.mjs "C:\path\to\NovelReader\library.json" --apply
```

The first command previews the changes. The second writes them and creates a `.before-genre-normalization.bak` copy beside the file. The script only splits a combined genre when all its space-separated parts are already present in the top-level `genres` array.
