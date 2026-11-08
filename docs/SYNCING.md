# Syncing from upstream AntennaPod

This fork never merges back. It pulls upstream `develop` in on occasion.

## Procedure

```sh
git fetch upstream
git checkout -b sync/upstream-<date> develop
git merge upstream/develop
# resolve conflicts, prefer our version on fork-owned files:
# net/discovery, ui/discovery, About page, versioning in app/build.gradle
./gradlew :app:assembleDebug   # must stay green
# install on device, walk Discover + Settings once
gh pr create --base develop --head sync/upstream-<date>
# merge, delete branch
```

## After a sync

1. If upstream bumped `versionCode`/`versionName` in `app/build.gradle`,
   rebase our `+ dsBuild` arithmetic onto the new base numbers.
2. If upstream touched files we forked (list above), re-verify those screens.
3. Push a fork release only if user-facing behavior changed.

## Conflict policy

- Our feature files win; upstream refactors underneath get adapted manually.
- Never force-push `develop`.
