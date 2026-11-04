# F-Droid Release Workflow

Adapted from the Lalumo workflow (pure Gradle project, no Capacitor).

## Files

- `metadata/com.smsbomb.app.yml` – F-Droid build metadata; a copy of this file
  is submitted to `fdroiddata` (https://gitlab.com/fdroid/fdroiddata)
- F-Droid display name: `O2 Free mit Weiter Speed Unlocker`
- `fastlane/metadata/android/{en-US,de}/` – store texts; F-Droid picks up
  `title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/<versionCode>.txt`
- `dev/add_changelog.sh` – generates fastlane changelogs and updates
  `CHANGELOG.md` / `CHANGELOG_de.md`
- `release.sh` – version bump (`-u`) + clean build + unit tests

## Release steps

1. `bash release.sh -u` – bumps minor version in `app/build.gradle.kts`,
   `dev/add_changelog.sh` (`VERSION_NAME`) and `metadata/com.smsbomb.app.yml`
2. Edit the `DE_CHANGES` / `EN_CHANGES` blocks in `dev/add_changelog.sh`
3. `bash dev/add_changelog.sh` – writes `changelogs/<versionCode>.txt` and
   updates `CHANGELOG.md` + `CHANGELOG_de.md`
4. Commit, tag as `v<version>` (e.g. `v1.0`) and push including tags
5. In the fdroiddata fork, update `metadata/com.smsbomb.app.yml`
   (`commit:` must point at the release tag/commit) and open an MR

`UpdateCheckMode: Tags` means F-Droid detects new versions via git tags —
always push the tag. `commit: vX.Y` in the `Builds` section must match a real
tag or commit sha.

## Notes

- License is GPL-3.0-or-later (same as Lalumo) — required for F-Droid.
- The repo must be public on GitHub; the URL in `metadata/com.smsbomb.app.yml`
  points to `github.com/rubo77/SMS-bomb` — adjust if the repo moves.
- F-Droid builds with the Gradle wrapper from the repo root; no `subdir` or
  `prebuild` needed since this is a plain Gradle project.
- Signing: F-Droid signs with its own key; release builds just need to compile.
