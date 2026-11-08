# NovelReader Extension API v1

Extensions are data packages described by an HTTPS `extension.json` manifest. The host installs and verifies the declared files, shows the extension's actions, and calls a built-in capability. It does not run downloaded Kotlin, Java, JavaScript, or native code.

## Supported capability

`chapter.text.transform.pairs.v1` accepts a JSON array of two-string arrays. The first value is the source text and the second is its replacement:

```json
[["می‌روم", "میرم"], ["خانه", "خونه"]]
```

The host applies these literal replacement rules to the open Chapter after the user confirms the action. File names and extension IDs are declared by the extension; the host has no special case for a particular extension.

## Manifest

```json
{
  "schema": 1,
  "apiVersion": 1,
  "id": "example-text-style",
  "name": "Example Text Style",
  "version": "1.0.0",
  "description": "A short description shown in the extension list.",
  "files": {
    "rules.json": {
      "sha256": "64-character-lowercase-sha256-of-the-file",
      "maxBytes": 262144
    }
  },
  "actions": [
    {
      "id": "apply-style",
      "label": "Apply style",
      "icon": "scan_text",
      "placement": "chapter_bottom",
      "capability": "chapter.text.transform.pairs.v1",
      "dataFile": "rules.json",
      "confirmTitle": "Apply style",
      "confirmMessage": "This action replaces text in the current Chapter."
    }
  ]
}
```

The host resolves each declared file relative to the manifest URL. File names must be simple names without directories, and downloads must remain on the manifest's HTTPS host. The manifest is limited to 32 KB; each extension may declare up to 24 files and 32 actions, and each file is limited to 1 MB. The host verifies every file against its SHA-256 before saving it under `Documents/NovelReader/.plugins/<id>/`.

The extension is initially disabled after installation. The user can enable, update, or remove it from the Extensions page. Update uses the manifest URL entered at installation.

## Compatibility

An extension that uses an already supported API capability does not need a matching app release. Update its own manifest and data files, publish them, and the user can update the extension independently. A new capability requires a host API addition and a new `apiVersion`; extensions must declare the API version they need.
