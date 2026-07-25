# Build, Test and Release

> **Scope:** the developer workflow — toolchain, Gradle tasks, what the test suite actually covers, versioning, and how this repo reaches the RuneLite Plugin Hub.
> **Key packages:** build tooling, `com.osrstcg` (test sources)
> **Related:** [Architecture Overview](01-architecture-overview.md) · [Persistence and Saves](06-persistence-and-saves.md) · [Packs and Rarity](08-packs-and-rarity.md) · [Utilities](15-utilities.md)

## Purpose

This is a single-module Gradle project with no CI, no linter, no code-coverage
tooling and three test files. Everything you need to build, run and ship it lives
in [build.gradle](../build.gradle) (72 lines) and
[runelite-plugin.properties](../runelite-plugin.properties) (7 lines).

The unusual parts are all consequences of how RuneLite external plugins work. The
RuneLite client is a `compileOnly` dependency because the plugin is loaded *into*
a client that already provides it — but it is also a `testImplementation`
dependency, because the way you run your plugin during development is to boot a
real client from the test source set. That is why
[OsrsTcgPluginTest](../src/test/java/com/osrstcg/OsrsTcgPluginTest.java) lives
under `src/test` despite containing no assertions: it is a `main()` launcher
wearing a test's clothes.

The two genuine test classes cover the persistence layer and nothing else. That is
a deliberate-looking choice — the persistence layer is the part where a bug
silently destroys a player's collection, and it is also the only part of the
codebase that can be exercised without a live game client. The service, UI, overlay
and party/trading layers have zero automated coverage. See
[What is actually tested](#what-is-actually-tested).

## File reference

| File | Lines | Responsibility |
|---|---|---|
| [build.gradle](../build.gradle) | 72 | Repositories, dependencies, `release 11`, and the `run` / `shadowJar` tasks |
| [settings.gradle](../settings.gradle) | 1 | `rootProject.name = 'runelite-tcg'` — feeds the shadow jar filename |
| [runelite-plugin.properties](../runelite-plugin.properties) | 7 | Plugin Hub manifest: display name, author, tags, version, entry class |
| [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) | 8 | Pins Gradle 8.10 with a SHA-256 checksum |
| [.gitignore](../.gitignore) | 8 | Ignores `.gradle`, `build`, `RuneLiteAPI`, `bin/`, and `PacksDebug.json` |
| [LICENSE](../LICENSE) | 24 | BSD 2-Clause, "Copyright (c) 2026, Azderi" |
| [README.md](../README.md) | 38 | User-facing description, acknowledgments, beta/trading disclaimer |
| [OsrsTcgPluginTest.java](../src/test/java/com/osrstcg/OsrsTcgPluginTest.java) | 13 | Dev-client launcher — **not a test** |
| [TcgStateCodecTest.java](../src/test/java/com/osrstcg/persist/TcgStateCodecTest.java) | 260 | JSON schema load/upgrade/round-trip, schema 3 → 5 → 6 |
| [TcgStateMigrationTest.java](../src/test/java/com/osrstcg/persist/TcgStateMigrationTest.java) | 323 | Config-blob → disk migration and load-source priority |

## Prerequisites

You need a JDK and nothing else — the Gradle wrapper fetches Gradle itself.

**JDK version.** The compile tasks set `options.release.set(11)`
([build.gradle:37](../build.gradle#L37)), which is the `javac --release 11` flag.
That means the *bytecode target and the visible JDK API are both Java 11*,
regardless of which JDK you actually run Gradle with. You cannot accidentally use a
Java 17 API and have it compile — `--release` swaps in the Java 11 API signatures,
unlike the older `sourceCompatibility`/`targetCompatibility` pair which only sets
the class file version. Java 11 is also the floor RuneLite targets.

In practice: build with JDK 11 or newer. Gradle 8.10 supports running on JDK 8
through 22, so a modern JDK 17 or 21 is fine — the `--release 11` setting keeps the
output compatible either way. Source encoding is pinned to UTF-8
([build.gradle:36](../build.gradle#L36)); do not remove that, several source files
contain non-ASCII characters (for example the `≥` in
[CollectionSetCompletionUtil](../src/main/java/com/osrstcg/service/CollectionSetCompletionUtil.java#L23)).

**The Gradle wrapper.** Always invoke `./gradlew` (or `gradlew.bat` on Windows),
never a system `gradle`. The wrapper pins:

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-all.zip
distributionSha256Sum=682b4df7fe5accdca84a4d1ef6a3a6ab096b3efd5edf7de2bd8c758d95a93703
validateDistributionUrl=true
```

([gradle-wrapper.properties:3-4](../gradle/wrapper/gradle-wrapper.properties#L3-L4)).
The `distributionSha256Sum` line means a tampered or truncated Gradle download
fails loudly instead of executing. The `-all` distribution (rather than `-bin`)
ships sources and docs, which is what IDE Gradle integration wants for
autocomplete in build scripts. Bumping Gradle means updating **both** the URL and
the checksum — run `./gradlew wrapper --gradle-version <x>` rather than hand-editing.

## Dependencies

```groovy
compileOnly          net.runelite:client:latest.release
compileOnly          org.projectlombok:lombok:1.18.30
annotationProcessor  org.projectlombok:lombok:1.18.30
testImplementation   net.runelite:client:latest.release
testImplementation   net.runelite:jshell:latest.release
testImplementation   junit:junit:4.13.2
```

([build.gradle:21-30](../build.gradle#L21-L30))

### Why the RuneLite client is both `compileOnly` and `testImplementation`

These are two different jobs, and the split is the single most important thing to
understand about this build file.

`compileOnly` covers **compiling the plugin**. At runtime the plugin is a small jar
dropped into a RuneLite client that already has `net.runelite:client` and its
entire transitive tree on its classpath. If the dependency were `implementation`,
the client classes would be candidates for packaging into the plugin artifact and
would appear twice at runtime — different `Class` objects for the same name,
loaded by different classloaders, producing `ClassCastException`s and failed
`instanceof` checks against RuneLite's own types. `compileOnly` gives you the API
to compile against and contributes nothing to any runtime or publish classpath.

`testImplementation` covers **running the client**. The `run` task uses
`sourceSets.test.runtimeClasspath`
([build.gradle:41](../build.gradle#L41)), so the client jar has to be on *some*
classpath for `RuneLite.main(args)` to exist. The test source set is where it
lands. The same classpath is what `shadowJar` packages
([build.gradle:49, 58](../build.gradle#L49)) — which is why the fat jar is built
from `testRuntimeClasspath` rather than `runtimeClasspath`.

Lombok follows the same shape for a different reason: `compileOnly` because the
annotations are `SOURCE`/`CLASS`-retention and are not needed at runtime, plus
`annotationProcessor` so the generated code is actually produced. Both must be
present; dropping either breaks the build in a confusing way (missing symbol errors
for methods Lombok should have generated).

`net.runelite:jshell` is test-only and exists to support RuneLite's developer-mode
JShell console, reachable when the client is launched with `--developer-mode`.

### The `latest.release` hazard

`runeLiteVersion` is the literal string `'latest.release'`
([build.gradle:18](../build.gradle#L18)) — a Gradle dynamic version selector, not a
pinned coordinate. Builds are therefore **not reproducible**: the same commit built
a month apart can resolve different RuneLite client versions, and a breaking change
upstream turns a previously green build red with no local change. This is the
convention RuneLite's own plugin template uses (the Plugin Hub always builds
against current), so it is intentional — but when you hit a compile error you did
not cause, check whether the client version moved before you start debugging your
own code. `./gradlew --refresh-dependencies` forces re-resolution;
`./gradlew dependencies` shows what actually resolved.

### Repositories

```groovy
maven { url = 'https://repo.runelite.net'
        content { includeGroupByRegex("net\\.runelite.*") } }
maven { url = 'https://repo1.maven.org/maven2' }
mavenCentral()
```

([build.gradle:5-16](../build.gradle#L5-L16))

The `content { includeGroupByRegex(...) }` filter on the RuneLite repository is a
security and performance control: Gradle will only ever ask `repo.runelite.net` for
artifacts whose group starts with `net.runelite`. Without it, every dependency
lookup would hit that host first, and a compromised or typo-squatted artifact there
could shadow a Maven Central coordinate. Keep the filter if you add repositories.
The explicit `repo1.maven.org` entry and `mavenCentral()` point at the same host and
are redundant, but harmless.

## Gradle tasks

### Build

```bash
./gradlew build
```

The standard lifecycle task: compiles `src/main/java` and `src/test/java`,
processes resources, runs the test suite, and produces
`build/libs/runelite-tcg-0.17.4.jar`. Because `build` depends on `test`, and `test`
needs the RuneLite client on its classpath, the first invocation on a clean machine
downloads the full client dependency tree — expect it to be slow once and fast
afterwards.

### Test

```bash
./gradlew test
```

Runs JUnit 4 (`junit:junit:4.13.2`,
[build.gradle:29](../build.gradle#L29)). There is no `useJUnitPlatform()` call, so
Gradle uses the JUnit 4 runner — annotations come from `org.junit.Test`, not
`org.junit.jupiter.api.Test`. Adding a JUnit 5 test without also switching the test
framework will result in a test that is silently never executed, which is the
classic trap here.

`OsrsTcgPluginTest` is picked up by Gradle's test scanner (its name ends in
`Test`) but contains no `@Test` method, so it contributes nothing and does not
boot a client. Results land in `build/reports/tests/test/index.html`.

To run one class:

```bash
./gradlew test --tests 'com.osrstcg.persist.TcgStateCodecTest'
```

### Run — boots a real RuneLite client

```bash
./gradlew run
```

This is how you develop the plugin. The task is a hand-registered `JavaExec`
([build.gradle:40-46](../build.gradle#L40-L46)):

```groovy
tasks.register('run', JavaExec) {
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.osrstcg.OsrsTcgPluginTest'
    jvmArgs "-ea", "-Xmx768m"
    args "--developer-mode", "--debug"
}
```

It launches a full, real RuneLite client in a fresh JVM with your plugin
pre-registered. Piece by piece:

- **`classpath = sourceSets.test.runtimeClasspath`** — the only classpath that has
  both the RuneLite client (a `testImplementation` dependency) and your compiled
  plugin classes.
- **`mainClass`** is the launcher described below, not a normal `main`.
- **`-ea`** enables assertions. RuneLite's own code contains assertions that catch
  client-thread violations; leaving this on during development is how you find out
  you touched the client off-thread.
- **`-Xmx768m`** caps the heap. RuneLite's own launcher uses a similar figure;
  raise it if you are profiling large collections, but developing under the same
  ceiling as real users is the point.
- **`--developer-mode`** unlocks RuneLite's developer tools — the widget inspector
  (essential for finding widget group/child ids like the `378`/`0` pair in
  [GameWidgetUtil](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L14)), the
  object/NPC inspectors, and the JShell console backed by the `net.runelite:jshell`
  dependency.
- **`--debug`** raises RuneLite's log level, which is what makes the plugin's
  `@Slf4j` `log.debug(...)` calls visible.

Note the client this launches uses your **real RuneLite profile directory**
(`~/.runelite`). Plugin state written during a `run` session goes to the same
config and save files a normal client would use — see the disk save behaviour
exercised by
[TcgStateMigrationTest](../src/test/java/com/osrstcg/persist/TcgStateMigrationTest.java).
Log in on a throwaway account if you are testing migration paths.

### shadowJar — the fat jar

```bash
./gradlew shadowJar
```

Produces `build/libs/runelite-tcg-0.17.4-all.jar`
([build.gradle:71](../build.gradle#L71) — the name comes from
`rootProject.name` in [settings.gradle](../settings.gradle#L1) plus `project.version`).

Despite the name, **this is not the Gradle Shadow plugin.** It is a plain `Jar`
task hand-configured to behave like one
([build.gradle:48-72](../build.gradle#L48-L72)):

```groovy
dependsOn configurations.testRuntimeClasspath
manifest { attributes('Main-Class': pluginMainClass, 'Multi-Release': true) }
duplicatesStrategy = DuplicatesStrategy.EXCLUDE
from sourceSets.main.output
from sourceSets.test.output
from { configurations.testRuntimeClasspath.collect { f -> f.isDirectory() ? f : zipTree(f) } }
```

The `zipTree` fold is the fat-jar mechanism: every dependency jar on
`testRuntimeClasspath` is unzipped and its entries copied in.
`duplicatesStrategy = EXCLUDE` makes first-writer-wins for colliding entries, which
is what stops the build failing on the many duplicate `META-INF` files across
RuneLite's dependency tree — but it also means service-loader files
(`META-INF/services/*`) are **not merged**, only the first is kept. The real Shadow
plugin has a `mergeServiceFiles()` for exactly this. If you ever add a dependency
that relies on `ServiceLoader`, expect it to break here.

The four exclusions matter:

| Exclusion | Why |
|---|---|
| `META-INF/INDEX.LIST` | A stale jar index in a repackaged jar makes the classloader fail to find classes |
| `META-INF/*.SF`, `*.DSA`, `*.RSA` | Signature files from signed dependencies; keeping them makes the fat jar fail signature verification |
| `**/module-info.class` | JPMS descriptors from multiple modules collide and confuse the classpath-mode launcher |

`'Multi-Release': true` in the manifest preserves `META-INF/versions/*` handling for
dependencies that ship version-specific classes.
`group = BasePlugin.BUILD_GROUP` ([build.gradle:69](../build.gradle#L69)) just files
the task under "build" in `./gradlew tasks` — `BasePlugin` resolves without an
import because it is one of Gradle's default script imports.

Because the manifest `Main-Class` is `OsrsTcgPluginTest`, the resulting jar is
directly runnable and starts the whole client with the plugin loaded:
`java -jar build/libs/runelite-tcg-0.17.4-all.jar`. This artifact is a
development/distribution convenience — it is **not** what the Plugin Hub consumes.

## OsrsTcgPluginTest is a launcher, not a test

This is the most confusing file in the repository, so it gets its own section. In
full ([OsrsTcgPluginTest.java](../src/test/java/com/osrstcg/OsrsTcgPluginTest.java)):

```java
package com.osrstcg;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsTcgPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OsrsTcgPlugin.class);
		RuneLite.main(args);
	}
}
```

No `@Test`. No assertion. No JUnit import. It is a `main()` method that registers
`OsrsTcgPlugin` as a built-in plugin and then hands control to RuneLite's own
entry point.

Why it lives in `src/test`:

1. The RuneLite client is only on the test classpath (`testImplementation`), so
   `RuneLite` and `ExternalPluginManager` are only resolvable from test sources.
2. Shipping a class that boots an entire game client inside `src/main` would put it
   in the plugin jar that gets loaded into a client — a client booting a client.
3. It is the convention RuneLite's official plugin template uses, so every
   RuneLite plugin repo has an identically-shaped `*PluginTest`.

`ExternalPluginManager.loadBuiltin` is the key call. It tells the client to treat
your plugin class as if it were compiled into the client, bypassing the Plugin Hub
download-and-verify path entirely. Your plugin then appears in the plugin list and
its `@Inject` graph, `@Subscribe` handlers and config panel all work exactly as
they would in production.

Practical consequences:

- **Ordering is fixed.** `loadBuiltin` must run before `RuneLite.main`. Reversing
  the two lines produces a client with no plugin and no error message.
- **`args` is forwarded verbatim.** Anything you append after `--args=` on a
  `./gradlew run` invocation reaches RuneLite's own argument parser, on top of the
  `--developer-mode --debug` already in the task config.
- **The name is load-bearing for confusion, not for behaviour.** You can rename it,
  but you would have to update `pluginMainClass`
  ([build.gradle:19](../build.gradle#L19)), which is referenced by both `run` and
  `shadowJar`'s manifest.
- **Gradle's test scanner picks it up and finds nothing.** This is why it does not
  boot a client during `./gradlew test`.

## What is actually tested

Two real test classes, both in `com.osrstcg.persist`, both JUnit 4. For what the
code under test actually does, see [Persistence and Saves](06-persistence-and-saves.md);
this section covers only what the tests pin down.

### TcgStateCodecTest — 9 tests, JSON schema handling

[TcgStateCodecTest](../src/test/java/com/osrstcg/persist/TcgStateCodecTest.java)
instantiates `new TcgStateCodec(new Gson())` directly
([line 23](../src/test/java/com/osrstcg/persist/TcgStateCodecTest.java#L23)) — no
Guice, no client — and drives it with hand-written JSON string constants
representing real historical save shapes. Its stated scope is every profile schema
written since 0.16.2
([lines 16-20](../src/test/java/com/osrstcg/persist/TcgStateCodecTest.java#L16-L20)).

| Test | What it pins down |
|---|---|
| `loadsSchema5From0162AndUpgradesToCurrentOnWrite` | Schema 5 `cardInstances` loads, is reported as `CURRENT_SCHEMA_VERSION`, and re-serialises as schema 6 `cardEntries` with `cardInstances` gone ([lines 93-95](../src/test/java/com/osrstcg/persist/TcgStateCodecTest.java#L93-L95)) |
| `loadsSchema5EmptyBaselineWithoutUpgradeFlag` | An explicitly-empty `skillCreditBaseline` is *not* the same as a missing one — `needsSchemaUpgradePersist()` stays false |
| `loadsSchema5MissingMetaFields` | A save with no `totalCreditsGained` / `profileCreatedAtUnix` defaults them to `0` and *does* set `needsSchemaUpgradePersist()` |
| `loadsCurrentSchema6CardEntries` | The current nested `cardEntries` → `variants` shape expands back into flat `OwnedCardInstance`s, preserving `locked` |
| `schema5EncodedConfigBlobRoundTripsThroughStorageEncoding` | `TcgStateStorageEncoding.encode` → `decode` → `fromJson` round-trips, the blob carries the `RLTCG_v2:` prefix, and `TcgStateHash.hexOfUtf8` is 64 hex chars and deterministic |
| `fromJsonUpgradesPre0162MissingSkillBaselineAndProfileMeta` | A schema **3** blob still loads, five schema versions later |
| `roundTripsCardEntriesWithVariants` | Three instances of one card collapse into `cardEntries`; `"foil":false` is omitted from output while `"foil":true` is written, and no `quantity` field is emitted ([lines 213-215](../src/test/java/com/osrstcg/persist/TcgStateCodecTest.java#L213-L215)) |
| `readsLegacyQuantityInVariants` | The dropped `quantity`/`lockedQuantity` fields are still *readable*, expanding to 2 instances with 1 locked |
| `roundTripsPresentSkillBaselineBySkillName` | Skill XP baselines persist by skill **name** string and resolve back to `Skill.ATTACK` / `Skill.COOKING` |

The write-side assertions are string `contains` checks against the serialised JSON
rather than structural comparisons. Blunt, but it is exactly what catches an
accidental field rename, which is the failure mode that costs players their
collections.

### TcgStateMigrationTest — 8 tests, storage migration

[TcgStateMigrationTest](../src/test/java/com/osrstcg/persist/TcgStateMigrationTest.java)
tests the hybrid config-blob-plus-disk storage model without touching RuneLite's
`ConfigManager`, using two private test subclasses at the bottom of the file:

- `TempDirFileStore extends TcgStateFileBackupStore`
  ([line 236](../src/test/java/com/osrstcg/persist/TcgStateMigrationTest.java#L236))
  overrides `saveDirectory()` and `currentProfileDirName()` to point at a JUnit
  temp directory, and is constructed with a `null` first argument in place of the
  real dependency.
- `TestableTcgStateStore extends TcgStateStore`
  ([line 265](../src/test/java/com/osrstcg/persist/TcgStateMigrationTest.java#L265))
  overrides six package-private config accessors
  (`writeProfileScoped`, `getProfileScoped`, `unsetProfileScoped`, and the three
  global equivalents) to read and write plain `HashMap`s.

This is the whole reason those accessors are package-private and overridable rather
than private — they are the seam that makes the store testable. If you inline them
or make them private, both test classes stop compiling. `moveOldStateIntoProfile()`
is stubbed to a no-op with an explicit comment
([line 320](../src/test/java/com/osrstcg/persist/TcgStateMigrationTest.java#L320)).

| Test | What it pins down |
|---|---|
| `migratesSchema5ConfigBlobToDiskAndUnsetsObsoleteKeys` | Loading from a schema-5 config blob seeds the master file and saves index on disk, and **unsets** `stateBackup`, `hashBackup`, `stateWrittenAt` while leaving `state` and `hash` intact |
| `migratesSchema6ConfigBlobToDisk` | Same seeding path for a current-schema blob |
| `seedsFromStateBackupWhenPrimaryMissing` | With only the backup keys present, load reports source `DISK` and still clears `stateBackup` |
| `doesNotReseedWhenMasterAlreadyExists` | An existing disk master is **not** overwritten by config migration — the returned state comes from config, but the master keeps its own older value |
| `saveConfigOnlyWritesStateAndHash` | Writes exactly two keys and the hash matches `TcgStateHash.hexOfUtf8(state)` |
| `loadFallsBackToMasterWhenConfigMissing` | Source `DISK` |
| `loadFallsBackToSnapshotWhenConfigAndMasterMissing` | Source `DISK_SNAPSHOT` |
| `loadPrefersConfigOverNewerDiskMaster` | Config wins even when the disk master holds different (999 vs 777 credits) data |

Between them, the last four lock in the load priority: **config → disk master →
disk snapshot**. That ordering is the kind of thing that gets "optimised" into
newest-wins by someone who does not know the history, and these tests are the guard
against it.

### What is not tested — honestly

Nothing outside `com.osrstcg.persist` has any coverage at all. Concretely, the
following are entirely untested:

| Package | Files | Contains |
|---|---|---|
| `service` | 28 | Pack opening, rarity rolls, credit awards, kill/XP tracking, duplicate selling, party trading, webhooks |
| `ui` | 14 | Sidebar panel, collection album, trade window, save/restore dialog |
| `party` | 13 | Party plugin integration, card transfer protocol |
| `model` | 15 | State records, serializers |
| `overlay` | 3 | Pack reveal overlay, credits infobox |
| `data` | 6 | Card catalog loading and normalisation |
| `util` | 9 | Everything in [Utilities](15-utilities.md) |

That is 88 of 102 main source files with no test touching them. The most
consequential gaps are the **rarity/economy math** (a wrong constant silently
changes every player's pull rates and can never be un-shipped) and the **trading
protocol** (a desync duplicates or destroys cards).

There is also no mocking library on the test classpath — only JUnit 4. Anything
requiring a stubbed `Client` currently needs a hand-written subclass, the same
technique `TcgStateMigrationTest` uses. Adding `org.mockito:mockito-core` as a
`testImplementation` dependency would unblock a large class of tests cheaply.

### Highest-value tests to add

Ranked by (risk of the code being wrong) ÷ (effort to test it). Everything in the
top tier is a pure static function needing zero setup:

1. **[RarityMath](../src/main/java/com/osrstcg/service/RarityMath.java)** (415
   lines). The `Tier` enum carries the pack tier-roll probabilities inline —
   `COMMON 37.34`, `UNCOMMON 32.0`, `RARE 16.0`, `EPIC 8.0`, `LEGENDARY 4.0`,
   `MYTHIC 2.0`, `GODLY 0.66`
   ([RarityMath.java:17-23](../src/main/java/com/osrstcg/service/RarityMath.java#L17-L23))
   — with a comment asserting they sum to 100%. They sum to 100.00. **Write the test
   that asserts that**, plus tier-boundary classification. This is the single
   highest-value test in the repo: pure, one line to set up, and it guards a number
   that changes player-visible odds.
2. **[DuplicateSellCredits](../src/main/java/com/osrstcg/service/DuplicateSellCredits.java)**
   (36 lines, fully pure). `creditsForRoundedScore` is
   `Math.max(MIN_CREDITS, score / SCORE_DIVISOR)` with `SCORE_DIVISOR = 200`,
   `MIN_CREDITS = 10`
   ([lines 7-18](../src/main/java/com/osrstcg/service/DuplicateSellCredits.java#L7-L18)).
   Integer division means everything below score 2000 pays exactly the floor.
   Three assertions cover the whole class.
3. **[DuplicateSellPlanner](../src/main/java/com/osrstcg/service/DuplicateSellPlanner.java)**
   (163 lines). Decides which copies to sell "respecting per-instance locks"
   ([line 12](../src/main/java/com/osrstcg/service/DuplicateSellPlanner.java#L12)).
   Takes plain lists of `OwnedCardInstance` and `CardDefinition` and returns an
   immutable `Result`. A lock-handling bug here destroys cards a player explicitly
   protected — and it is testable with no mocks at all.
4. **[CollectionSetCompletionUtil](../src/main/java/com/osrstcg/service/CollectionSetCompletionUtil.java)**
   (97 lines, pure static). Takes a `Map<CardCollectionKey, Integer>` and returns
   set-completion results. Foil/non-foil merging is exactly the kind of rule that
   drifts from the album's own display logic.
5. **The pure utilities** — `NumberFormatting`, `PackRevealZoomUtil`,
   `HtmlEntities`, `PullNotificationMessages`. See the
   [testability table in the utilities doc](15-utilities.md#testability) for the
   specific edge cases worth covering.
6. **`CardEntrySerializer`** in `model`. It is the other half of the schema-6
   format that `TcgStateCodecTest` only exercises end-to-end.

Deliberately *not* on this list: anything in `ui`, `overlay` or `party`. Those need
a live client or an EDT harness, and the effort-to-value ratio is far worse.

## Versioning

Two files carry the version and **they must match**:

| File | Line | Value |
|---|---|---|
| [build.gradle](../build.gradle#L33) | 33 | `version = '0.17.4'` |
| [runelite-plugin.properties](../runelite-plugin.properties#L5) | 5 | `version=0.17.4` |

There is nothing enforcing this. No Gradle task reads the properties file, no test
asserts equality, and there is no CI to catch a mismatch — the repo has no
`.github` directory at all. **Bumping one and forgetting the other is a live
failure mode.** The Gradle `version` determines the built artifact filename
(`runelite-tcg-0.17.4-all.jar`,
[build.gradle:71](../build.gradle#L71)); the properties `version` is what the
Plugin Hub displays and uses for update detection. A drift means users see one
number and run another, and hub-side update prompts can fire against the wrong
value.

The history shows this is handled as a single manual commit —
`f015591 Version 0.17.4` in `git log`. Keep that discipline: one commit, both
files, nothing else. If you want to remove the footgun, the cheap fix is a Gradle
task that reads `runelite-plugin.properties` and fails the build on mismatch, wired
into `check`.

## Plugin Hub submission

`runelite-plugin.properties` is the entire manifest
([runelite-plugin.properties](../runelite-plugin.properties)):

```properties
displayName=OSRS TCG
author=Az
description=OSRS Card collection plugin
tags=tcg,cards,collection,packs
version=0.17.4
plugins=com.osrstcg.OsrsTcgPlugin
build=standard
```

| Key | Meaning |
|---|---|
| `displayName` | Name shown in the client's Plugin Hub list |
| `author` | Attributed author |
| `description` | One-line summary in the hub listing |
| `tags` | Comma-separated search keywords |
| `version` | Hub-visible version; must match `build.gradle` |
| `plugins` | Fully-qualified entry class(es) — here [`com.osrstcg.OsrsTcgPlugin`](../src/main/java/com/osrstcg/OsrsTcgPlugin.java). Note it names the *plugin* class, not the `OsrsTcgPluginTest` launcher |
| `build=standard` | Selects the hub's standard Gradle build pipeline |

The model is **build-from-source, not upload-a-jar**. The hub does not consume the
`shadowJar` artifact; it builds the repository itself and links the resulting jar
against the current client. That is precisely why the RuneLite client is
`compileOnly` and why `latest.release` is an acceptable version selector — the hub
always builds against current anyway.

The submission side of this lives in RuneLite's own `plugin-hub` repository, not
here: a plugin is registered there by a small file naming this repository's URL and
a specific commit hash, and releasing a new version means opening a pull request
that moves that hash forward. Because the pin is a commit hash, **pushing to `main`
does not ship anything** — an unshipped commit is invisible to users until the hub
pin moves. Note that no part of that pinning file is present in this repository, so
its exact current contents could not be verified from here; see
[Open questions](#open-questions).

Two consequences for day-to-day work:

- `./gradlew build` must pass on a clean checkout with no local files. Anything in
  [.gitignore](../.gitignore) is genuinely absent hub-side — in particular
  `src/main/resources/PacksDebug.json`, the "Debug-only pack definitions" file
  ([.gitignore:6-7](../.gitignore#L6-L7)). Code that requires it will fail on the
  hub while working perfectly on your machine.
- The `icon.png` at the repository root is the hub listing icon. There is a second,
  distinct `src/main/resources/icon.png` loaded at runtime by
  [CollectionAlbumWindow](../src/main/java/com/osrstcg/ui/collectionalbum/CollectionAlbumWindow.java#L77)
  and [TradeWindow](../src/main/java/com/osrstcg/ui/trade/TradeWindow.java#L51) as
  the Swing window icon. Different files, different purposes — do not consolidate.

Licensing is BSD 2-Clause ([LICENSE](../LICENSE)), "Copyright (c) 2026, Azderi",
which satisfies the hub's open-source requirement.

## Repo layout

```
tachyon-tcg/
├── build.gradle                    72 lines — the entire build
├── settings.gradle                 rootProject.name = 'runelite-tcg'
├── gradlew / gradlew.bat           wrapper scripts (always use these)
├── gradle/wrapper/                 gradle-wrapper.properties — Gradle 8.10 + SHA-256
├── runelite-plugin.properties      Plugin Hub manifest
├── icon.png                        hub listing icon (NOT the runtime one)
├── LICENSE                         BSD 2-Clause
├── README.md                       user-facing overview + beta/trading disclaimer
├── .gitignore                      .gradle, build, RuneLiteAPI, bin/, PacksDebug.json
├── docs/                           this documentation set
└── src/
    ├── main/java/com/osrstcg/      102 files
    │   ├── OsrsTcgPlugin.java      @PluginDescriptor entry point
    │   ├── OsrsTcgConfig.java      @ConfigGroup interface
    │   ├── data/         (6)       card catalog load + normalisation
    │   ├── model/        (15)      immutable state, card entries, serializers
    │   ├── overlay/      (3)       pack reveal overlay, credits infobox
    │   ├── party/        (13)      party plugin integration, card transfer
    │   ├── persist/      (12)      codec, storage encoding, hash, file/config stores
    │   ├── service/      (28)      pack opening, rarity, credits, trading, webhooks
    │   ├── ui/           (14)      sidebar panel + collectionalbum/, save/, trade/
    │   └── util/         (9)       pure helpers — see 15-utilities.md
    ├── main/resources/             flat, no subdirectories
    └── test/java/com/osrstcg/
        ├── OsrsTcgPluginTest.java          launcher, not a test
        └── persist/
            ├── TcgStateCodecTest.java      9 tests
            └── TcgStateMigrationTest.java  8 tests
```

### Resources

`src/main/resources` is flat — 14 files, no subdirectories:

| File(s) | Purpose |
|---|---|
| `Card.json` | The card catalog. Wiki-sourced, which is why [HtmlEntities](../src/main/java/com/osrstcg/util/HtmlEntities.java) exists |
| `Packs.json` | Booster pack definitions |
| `PacksDebug.json` | **Gitignored** ([.gitignore:7](../.gitignore#L7)) — debug-only pack definitions, absent from any clean checkout |
| `Cardback.png`, `Pack_Standard.png`, `Pack_Standard_thumbnail.png` | Card back and pack art |
| `icon.png` | Swing window icon for the album and trade windows |
| `credits.png`, `lock.png`, `Discord-Logo-White.png` | UI icons |
| `apex.wav`, `card.wav`, `flip.wav`, `hum.wav`, `reveal.wav`, `transfer.wav` | Pack-opening and transfer sound effects |

Card *images* are not bundled — they are fetched from the OSRS Wiki at runtime and
cached by
[WikiImageCacheService](../src/main/java/com/osrstcg/service/WikiImageCacheService.java),
which is why the jar stays small despite a large catalog.

## Threading

Build tooling has no threading model of its own, but the three ways of executing
code in this repo differ in a way that shapes what is testable:

| Entry point | Threads available |
|---|---|
| `./gradlew test` | A plain Gradle test-worker JVM. **No client thread, no RuneLite event bus, no initialised Swing EDT.** |
| `./gradlew run` | A full client: RuneLite's client thread, the Swing EDT, RuneLite's scheduled executor, and party websocket threads |
| `java -jar ...-all.jar` | Identical to `run` |

This is the mechanical reason the test suite stops at the persistence layer. `TcgStateCodec`
and `TcgStateStore` are plain objects with no thread affinity, so they run fine in a
test worker. Anything that reads `Client` state or builds a Swing component needs
the second row. Any test you add for the service layer must either target a pure
static class (see the suggestions above) or introduce the same subclass-override
seam that `TcgStateMigrationTest` uses.

## Coding conventions

These are observed from the source, not from a style file — there is no
`checkstyle.xml`, no `.editorconfig`, and no formatter config in the repository.
They match RuneLite's own house style, which is where they come from.

**Indentation: hard tabs.** Every source file in `src/main` and `src/test` uses tab
characters, not spaces. Continuation lines of a wrapped expression get one extra
tab. Configure your editor before you touch anything, or your first diff will be
100% whitespace.

**Braces: Allman.** Every brace goes on its own line, including for one-line `if`
bodies, which are never written without braces:

```java
if (client == null)
{
	return false;
}
```

([PlayerCombatUtil.java:20-23](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java#L20-L23))

**`final` on classes and fields, not on locals or parameters.** Utility classes are
uniformly `public final class X` with a `private X() {}` constructor — all nine
files in `util` follow it, and so do
[RarityMath](../src/main/java/com/osrstcg/service/RarityMath.java#L12),
[DuplicateSellCredits](../src/main/java/com/osrstcg/service/DuplicateSellCredits.java#L6)
and [TcgStateHash](../src/main/java/com/osrstcg/persist/TcgStateHash.java#L7).
Injected dependency fields are `private final`
([PlayerCombatMonitor.java:20](../src/main/java/com/osrstcg/service/PlayerCombatMonitor.java#L20)).
Local variables and method parameters are *not* marked `final` — that is a
consistent choice, not an oversight.

**Lombok, sparingly.** Usage across `src/main`:

| Annotation | Occurrences |
|---|---|
| `@Slf4j` | 20 |
| `@Data` | 15 |
| `@EqualsAndHashCode` | 12 |
| `@Value` | 4 |
| `@Getter` | 1 |

`@Slf4j` is the logging convention — no manual `LoggerFactory.getLogger` calls.
`@Data` and `@Value` cluster in `model` and `data` (mutable DTOs and immutable
value objects respectively); `@EqualsAndHashCode` usually accompanies them to pin
the fields that participate in identity, which matters for things like
`CardCollectionKey` being a valid `HashMap` key. Notably absent: `@Builder`,
`@RequiredArgsConstructor` and `@AllArgsConstructor`. Constructors are written out
by hand — see the injection convention below.

**Package organisation is by layer, not by feature.** A single feature such as pack
opening is spread across `data` (catalog), `model` (result types), `service`
(logic), `overlay` (reveal rendering) and `ui` (panel). Only `ui` has
sub-packages, split by window: `collectionalbum/`, `save/`, `trade/`. `util` is the
leaf — it depends on RuneLite API and the JDK, and on nothing else in `com.osrstcg`
(the sole exception being `PlayerCombatUtil` calling `PetNpcIds`, within the same
package).

**Constructor injection, not field injection.** There are 35 files containing
`@Inject`, and 26 of those put it on a constructor. The pattern is:

```java
@Singleton
public final class PlayerCombatMonitor
{
	private final Client client;

	@Inject
	public PlayerCombatMonitor(Client client)
	{
		this.client = client;
	}
```

([PlayerCombatMonitor.java:15-27](../src/main/java/com/osrstcg/service/PlayerCombatMonitor.java#L15-L27))

Dependencies are `private final`, assigned once, so a service can never be observed
half-constructed and is trivially constructible in a test with `new` — exactly the
property that makes the pure-service tests suggested above cheap to write.

Field injection (`@Inject private Foo foo;`) is confined to **one file**:
[OsrsTcgPlugin.java](../src/main/java/com/osrstcg/OsrsTcgPlugin.java), which holds
all 38 field-injected declarations in `src/main`. That is the correct exception —
RuneLite instantiates the `Plugin` subclass itself through its own plugin loader,
so constructor injection is not available there. Everywhere else, use a
constructor.

**Circular dependencies use `Provider`.** Where two singletons need each other,
the cycle is broken with `javax.inject.Provider` and resolved lazily at call time,
as in `PackSafeModeService`'s `tcgPanelProvider.get().refresh()`
([PackSafeModeService.java:121](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L121)).
Guice cannot construct a genuine constructor cycle; this is the escape hatch.

**Imports are explicit.** Zero wildcard imports and zero static imports across all
102 main source files. Ordering is a single block sorted by full package string
with no blank-line grouping, so `com.*` precedes `java.*`, `javax.*`, `lombok.*`,
`net.runelite.*` and `org.*` in that order. Sorting is not perfectly enforced —
[PlayerCombatMonitor.java:7-9](../src/main/java/com/osrstcg/service/PlayerCombatMonitor.java#L7-L9)
has `HitsplatApplied` before `GameTick` — but the single-block, no-wildcard shape
is universal.

**Javadoc where behaviour is non-obvious, absent where it is not.** The pattern
is a short class-level comment stating the *why* — for example the client-thread
requirement on
[GameWidgetUtil](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L6-L10) or
the sourcing note on
[PetNpcIds](../src/main/java/com/osrstcg/util/PetNpcIds.java#L9-L12) — rather than
per-method boilerplate. Follow that: document constraints and provenance, not
signatures.

### Open questions

- The exact Plugin Hub registration for this plugin (the repository URL and pinned
  commit hash in RuneLite's `plugin-hub` repo) is not present in this repository
  and could not be verified from the code here. The description of the pin-and-PR
  release flow above reflects the standard hub model, not something read from this
  repo.
- There is no `.github` directory and no CI configuration of any kind, so it could
  not be determined whether `build`/`test` are run automatically anywhere before a
  hub submission, or only locally by the maintainer.
- `net.runelite:jshell` is declared as a `testImplementation` dependency
  ([build.gradle:28](../build.gradle#L28)) but is not referenced anywhere in
  `src/test`. It is presumably there so the `--developer-mode` JShell console works
  under `./gradlew run`, but nothing in this repository states that explicitly.
