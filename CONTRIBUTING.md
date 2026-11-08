# Contributing to antennapod-discovery

Small independent fork — rules are short on purpose.

## Issues

Open an issue directly, no triage process. Include: app version
(`Settings → Project → About`), device + Android version, steps to reproduce,
and a logcat snippet on crashes.

## Pull requests

- Branch off `develop`: `feature/<short-name>`.
- Keep the diff minimal: only what the change needs.
- User-visible strings: update **both**
  `ui/i18n/src/main/res/values/strings.xml` and `values-zh-rCN/strings.xml`.
- Before opening: `./gradlew checkstyle lint` clean, plus install on a real
  device and walk through the changed screens once.
- PR description: 2–8 sentences, what + how verified. Link the issue if any.
- One feature per PR; delete the branch after merge.

## Upstream syncs

Covered separately in `docs/SYNCING.md`.
