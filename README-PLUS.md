# MCA Selector Plus

MCA Selector Plus is [MCA Selector](https://github.com/Querz/mcaselector) with an extension layer on
top: an NBT Studio style find & replace across region files and world data files
(`level.dat`, `data/`, `playerdata/`, `structures/`, ...), a browser for every NBT file of a world and
a data file editor with the same add/remove/edit capabilities as the native chunk editor.

The upstream core is never forked away from: this repository tracks `Querz/mcaselector` and re-applies
the Plus layer on top of every new release, so all upstream fixes and new Minecraft versions arrive
without manual porting.

## Version scheme

```
MCA Selector <core version> - Plus <plus version>
      └─ from upstream ─┘        └─ this repo ─┘
```

* the core version is read from `version` in `gradle.properties`, which is written by upstream and
  is never edited here by hand,
* the Plus version is read from `plus.version` in the same file and is bumped only when the extension
  layer changes,
* both end up in the jar manifest as `Application-Version` and `Plus-Version`, the window title and
  the about dialog show them as `MCA Selector 2.9 - Plus 1.0`,
* the jar is named `mca-selector-plus-<core>-plus-<plus>-all.jar`.

Example: upstream releases MCA Selector 2.9 → this repository shows `MCA Selector 2.9 - Plus 1.0`
and ships `mca-selector-plus-2.9-plus-1.0-all.jar`. When upstream moves to 2.10, the next sync
produces `MCA Selector 2.10 - Plus 1.0` without touching a single line of Plus code.

## Branch model

| branch | content | owner |
| --- | --- | --- |
| `master` | untouched mirror of `Querz/mcaselector`, fast-forward only | upstream |
| `plus`   | the extension layer, a linear history rebased onto `master` (default branch) | this repo |

Rules that keep the sync cheap:

* never commit to `master`, it is only fast-forwarded,
* keep Plus commits small and focused so a rebase stops at the right commit,
* do not reformat upstream code and do not reorder upstream files,
* keep upstream files (`README.md`, `LICENSE`, `gradle.properties` version lines, `installer/`) as
  close to upstream as possible; Plus-only settings go into the `plus.*` keys at the bottom of
  `gradle.properties` or into new files.

## Staying in sync with upstream

Automatically: `.github/workflows/sync-upstream.yml` runs daily (and on demand), fast-forwards
`master`, rebases `plus`, pushes the result and opens an issue when a conflict needs a human.

Manually:

```bash
scripts/sync-upstream.sh      # fetch upstream, fast-forward master, rebase plus
./gradlew test shadowJar      # verify
git push -f origin plus       # only after a clean rebase
```

Both paths do the same thing. Nothing is ever force-pushed when the rebase did not finish, so a
conflicting sync leaves the branch exactly as it was.

## Building

```bash
./gradlew shadowJar           # build/libs/mca-selector-plus-<core>-plus-<plus>-all.jar
./gradlew test                # jUnit tests, GUI tests are skipped on macOS
java -jar build/libs/mca-selector-plus-*-all.jar
```

Requires JDK 21. The fat jar needs JavaFX on the module path on Linux, on macOS the bundled JDK is
enough for development builds.

## Releasing

1. sync with upstream (`scripts/sync-upstream.sh`),
2. bump `plus.version` when the extension layer changed,
3. `./gradlew test shadowJar`,
4. tag `plus-<core>-<plus>`, e.g. `plus-2.9-1.0`,
5. attach `build/libs/mca-selector-plus-*-all.jar` to the release.

## What the Plus layer adds

See [README-NBT-STUDIO.md](README-NBT-STUDIO.md) for the feature description, the keyboard shortcuts
and the file formats that are searched.

## License

MIT, same as the upstream project. The upstream copyright stays untouched, the Plus layer is
copyright of its authors.
