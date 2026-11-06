# MCA Selector Plus

MCA Selector Plus is [MCA Selector](https://github.com/Querz/mcaselector) with an extension layer on
top: an NBT Studio style find & replace across region files and world data files
(`level.dat`, `data/`, `playerdata/`, `structures/`, ...), a browser for every NBT file of a world and
a data file editor with the same add/remove/edit capabilities as the native chunk editor.

The upstream core is never forked away from: this repository tracks `Querz/mcaselector` and re-applies
the Plus layer on top of every new release, so all upstream fixes and new Minecraft versions arrive
without manual porting.

## Download and run

Every release ships the same set of artefacts upstream ships, built by the same jpackage + Inno Setup
pipeline, with a bundled JRE so no Java installation is needed:

| platform | file |
| --- | --- |
| Windows | `mca-selector-plus-<core>-plus-<plus>-setup.exe` |
| macOS, Apple silicon | `mca-selector-plus-<core>-plus-<plus>-aarch64.dmg` |
| macOS, Intel | `mca-selector-plus-<core>-plus-<plus>-x64.dmg` |
| Linux, Debian/Ubuntu x64 | `mca-selector-plus-<core>-plus-<plus>-x64.deb` |
| Linux, Debian/Ubuntu arm64 | `mca-selector-plus-<core>-plus-<plus>-aarch64.deb` |
| Linux, Fedora/RHEL x64 | `mca-selector-plus-<core>-plus-<plus>-x64.rpm` |
| Linux, Fedora/RHEL arm64 | `mca-selector-plus-<core>-plus-<plus>-aarch64.rpm` |

The Windows installer uses its own `AppId`, and the macOS bundle its own identifier
(`net.querz.mcaselector.plus`), so MCA Selector Plus can be installed next to an upstream MCA Selector
without either one overwriting the other.

If you prefer a jar, grab `mca-selector-plus-<core>-plus-<plus>-all.jar` from the same
[releases page](https://github.com/RunningVAN/mca-selector-plus/releases) and run it with JDK 21:

```bash
java -jar mca-selector-plus-2.9-plus-1.0-all.jar
```

On Linux, JavaFX has to be on the module path (`--module-path` / `--add-modules javafx.controls,javafx.fxml`),
same as for upstream MCA Selector. On macOS and Windows the jar runs as it is.

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
4. tag `plus-<core>-<plus>`, e.g. `plus-2.9-1.0`, and push the tag:

   ```bash
   git tag plus-2.9-1.0 && git push origin plus-2.9-1.0
   ```

`.github/workflows/release-plus.yml` picks the tag up and runs the same packaging jobs upstream runs:
the Inno Setup installer on `windows-latest`, the dmg on `macos-latest` (aarch64) and `macos-26-intel`
(x64), the deb and rpm in `debian:bookworm` / `fedora:latest` containers for both architectures, and
the fat jar on Ubuntu. All artefacts are published in one release, with the versions read from
`gradle.properties` so the tag, the release title, the installer names and the `Application-Version` /
`Plus-Version` manifest entries can never drift apart. Upstream's own installer workflow only runs for
releases published from `master`, so the two never collide.

Current release: **plus-2.9-1.0** — `-setup.exe`, `-aarch64.dmg`, `-x64.dmg`, `-x64.deb`,
`-aarch64.deb`, `-x64.rpm`, `-aarch64.rpm` and `-all.jar`.

## What the Plus layer adds

Everything upstream does — map rendering, selections, import/export, delete, crop, chunk and NBT
editing — still works exactly as before. The Plus layer adds a second way to work on NBT data: a
NBT Studio style engine that treats a whole world as one searchable tag tree, available both in the
GUI and on the command line.

### 1. World-wide NBT search

Find tags by name, by value, by both, by regular expression, or by wildcard, across

* region, poi and entities files of every dimension,
* and — when the option is on — every world data file: `level.dat`, `data/`, `playerdata/`,
  `structures/` and the per-dimension copies of those directories,
* restricted to a subtree (`path=sections[*].palette`) or to a tag type (`type=numeric`).

A match is reported per chunk (any tag inside the chunk hits) or per file for the world data files.
Matching chunks become a normal map selection, so the result can be fed into every existing upstream
operation, and the same window lists every hit with its path, so a double click opens that chunk in
the editor.

### 2. World-wide NBT replace

The same query language drives a replacer that rewrites name, value or both, supports regular
expressions with capture groups (`minecraft:diamond_block` → `mca:diamond` via `$1`), and runs over
a selection or over the entire world. A confirmation dialog shows how many chunks and files are
about to be modified before anything is written.

### 3. Direct editing of the tag tree

The chunk editor and the new data file editor share one options bar: delete the selected tag, or
insert any of the twelve `Tag.Type` variants (`BYTE`, `SHORT`, `INT`, `LONG`, `FLOAT`, `DOUBLE`,
`String`, `List`, `Compound`, `[B]`, `[I]`, `[L]`). Type buttons enable themselves according to the
selected parent node, so no query string is needed for small edits.

### 4. Data file editor

`level.dat`, `data/`, `playerdata/`, `structures/` and the per-dimension equivalents can be opened
in a tabbed editor with the same tree and the same add/remove/edit operations as the chunk editor,
without going through a search first.

### 5. NBT file browser

`Tools → Browse NBT files…` lists every editable file of the loaded world grouped by directory.
Double click opens it in the editor. This is the entry point for "I know the file, just let me edit
it", independent of the search window.

### 6. First-class in filters and CLI

The search is exposed as the `NBT` filter type and the replace as the `NbtReplace` field type, so
both work wherever upstream filters and fields work:

```bash
# which chunks contain minecraft:stone (writes a selection csv)
java -jar mca-selector-plus-2.9-plus-1.0-all.jar --mode select \
  -w /path/to/world --query 'NBT contains "value=minecraft:stone"' -o stone.csv

# rewrite the whole world
java -jar mca-selector-plus-2.9-plus-1.0-all.jar --mode change \
  -w /path/to/world --fields 'NbtReplace="value=minecraft:stone mode=exact => minecraft:dirt"'
```

### 7. Version display

Window title, about dialog and jar manifest show `MCA Selector <core> - Plus <plus>`, so a bug
report always tells which core and which extension layer produced it. New UI strings are translated
in all 19 bundled languages.

The full reference — query keys, matching modes, GUI walkthrough, CLI examples and the list of
supported files — is in [README-NBT-STUDIO.md](README-NBT-STUDIO.md) (Chinese).

## License

MIT, same as the upstream project. The upstream copyright stays untouched, the Plus layer is
copyright of its authors.
