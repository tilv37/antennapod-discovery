# antennapod-discovery

AntennaPod fork by Shuo. Independent development — no merges back upstream,
but upstream updates are merged in periodically (see `docs/SYNCING.md`).

Base: AntennaPod `3.13.0-beta1` · this fork's versions append `.dsN`
(e.g. `3.13.0-beta1.ds3`). License: GPL-3.0 (see `LICENSE`), same as upstream.

## What is different from upstream

- Discover page: country + genre toplists, real Apple charts top episodes,
  episode dialog, press feedback everywhere, single-fetch loading
- About page states this is a modded build and links this repo
- APK file names carry the version, e.g.
  `antennapod-free-release-3.13.0-beta1.ds3(3130004).apk`

## Build

Requirements: JDK 21, Android SDK (`ANDROID_HOME` set).

```sh
./gradlew :app:assembleDebug
```

Debug builds install with `./gradlew --console=plain :app:installPlayDebug`.

Release builds are self-signed with a local keystore (`app/release.keystore`,
gitignored — back it up, reinstalls need the same key):

```sh
./gradlew :app:assembleFreeRelease \
  -PreleaseStoreFile=release.keystore \
  -PreleaseStorePassword=<password> \
  -PreleaseKeyAlias=<alias> \
  -PreleaseKeyPassword=<password>
```

Prefer the manual `Fork Release` GitHub Action: it builds the signed APK and
publishes it to the Releases page. It needs four repository secrets:
`KEYSTORE_BASE64` (base64 of the keystore), `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Workflow

- `develop` is the main branch. Never commit to it directly.
- One feature branch per change, PR into `develop`, squash or merge, delete the branch.
- Before a PR: `./gradlew checkstyle lint` plus on-device smoke test.
- User-visible strings live in `ui/i18n/src/main/res/values/strings.xml` (English)
  **and** `values-zh-rCN/strings.xml` (Chinese) — both edited by hand, no
  translation platform in this fork.

See `CONTRIBUTING.md` (short version) and `docs/SYNCING.md` (upstream sync).
