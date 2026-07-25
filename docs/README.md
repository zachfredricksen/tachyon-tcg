# OSRS TCG — Developer Documentation

Technical documentation for the OSRS TCG RuneLite plugin, written for contributors. It assumes Java familiarity but not familiarity with this codebase or with RuneLite plugin development.

Every factual claim is cited to a specific file and line. If a doc and the code disagree, the code is right — please fix the doc.

## Reading paths

**New to the codebase?** Read in this order:

1. [Architecture Overview](01-architecture-overview.md) — layering, dependency graph, threading model, the two gameplay loops.
2. [Plugin Lifecycle](02-plugin-lifecycle.md) — the entry point, event wiring, and chat commands.
3. [State Model](05-state-model.md) — immutable state and the card identity model.
4. [Persistence & Saves](06-persistence-and-saves.md) — read this before touching anything that writes.

**Here for a specific task?**

| I want to… | Read |
|---|---|
| Add a card or a booster pack | [Card Catalog & Data](04-card-catalog-and-data.md) |
| Change drop rates or rarity tiers | [Packs & Rarity](08-packs-and-rarity.md) |
| Change how credits are earned | [Credits & Economy](07-credits-and-economy.md) |
| Add a sidebar control | [Sidebar Panel](10-sidebar-panel.md) |
| Touch the reveal animation or card art | [Pack Reveal & Rendering](09-pack-reveal-and-rendering.md) |
| Work on trading | [Party & Trading](12-party-and-trading.md) |
| Add or change a notification | [Notifications](13-notifications.md) |
| Understand what leaves the user's machine | [External Services](14-external-services.md) |
| Build, run, or release the plugin | [Build, Test & Release](16-build-test-and-release.md) |

## All documents

| # | Document | Covers |
|---|---|---|
| 01 | [Architecture Overview](01-architecture-overview.md) | Package layering, DI graph and the `Provider<T>` cycle breaks, the four-context threading model, the earn→open→collect and trade loops. |
| 02 | [Plugin Lifecycle](02-plugin-lifecycle.md) | `startUp()` / `shutDown()` ordering and why it matters, the full `@Subscribe` event table, all 12 party message registrations, every `::tcg-*` command and the public `!tcg` command, profile switching. |
| 03 | [Configuration](03-configuration.md) | All 24 keys in the `osrstcg` group with type, default and the exact consuming class. Reactive vs polled keys. The web-share privacy warning. |
| 04 | [Card Catalog & Data](04-card-catalog-and-data.md) | `Card.json` / `Packs.json` schemas, the dual item/monster entry shape and the Gson adapter it forces, real catalog counts, how to add a card or pack. |
| 05 | [State Model](05-state-model.md) | `TcgState` and its copy-on-write builders, and the card identity model — `CardCollectionKey` vs `CardEntry` vs `OwnedCardInstance` vs `CardVariant`. |
| 06 | [Persistence & Saves](06-persistence-and-saves.md) | The three storage tiers, the codec format, `RLTCG_v2` encoding, hashing, shape-driven migration, the load cascade, save triggers, and a failure-mode table. |
| 07 | [Credits & Economy](07-credits-and-economy.md) | All seven credit sources with real formulas, the skill-baseline settle machine, reward tuning draft/commit, safe mode, credits/hour. |
| 08 | [Packs & Rarity](08-packs-and-rarity.md) | The buy-and-open transaction and its atomicity, derived rarity tiers, the weight table with computed per-card odds, the apex path, duplicate sell-back. |
| 09 | [Pack Reveal & Rendering](09-pack-reveal-and-rendering.md) | The reveal state machine, per-frame layout and animation, input consumption, `SharedCardRenderer` compositing and foil effects, the wiki image cache. |
| 10 | [Sidebar Panel](10-sidebar-panel.md) | `TcgPanel` section by section, the refresh model, the reveal freeze, the reward-tuning draft lifecycle, debug mode, EDT rules. |
| 11 | [Collection Album](11-collection-album.md) | The detached album window, paging and layout, filters and sort modes, the variant drill-down, provenance tooltips, refresh strategy. |
| 12 | [Party & Trading](12-party-and-trading.md) | The party primer, all 12 message types, the full trade handshake and state machine, the gift path, and the trust model. |
| 13 | [Notifications](13-notifications.md) | The four-channel fan-out, both eligibility predicates as decision tables, Dink hand-off, the Discord webhook payload. |
| 14 | [External Services](14-external-services.md) | Every network egress, the collection-share upload contract and payload, credential storage, the wiki image client, privacy analysis. |
| 15 | [Utilities](15-utilities.md) | The `com.osrstcg.util` package class by class, with a testability ranking. |
| 16 | [Build, Test & Release](16-build-test-and-release.md) | Toolchain, Gradle tasks, the `run` launcher, actual test coverage and its gaps, versioning, Plugin Hub submission, code conventions. |

## Conventions used in these docs

- Source links point at a file and line: `[PackOpeningService.java:118](../src/main/java/com/osrstcg/service/PackOpeningService.java#L118)`.
- Each document opens with a **Scope / Key packages / Related** header block.
- Each closes with **Gotchas & invariants** — the things that will bite you when editing that area — and, where something could not be resolved from the source, an **Open questions** section.
- Formulas and constants are reproduced from the code rather than paraphrased.

## Maintaining these docs

The docs describe the code as of version **0.17.4**. The areas most likely to drift are the ones with hard numbers in them: the rarity weight table in [Packs & Rarity](08-packs-and-rarity.md), the credit formulas in [Credits & Economy](07-credits-and-economy.md), the config table in [Configuration](03-configuration.md), and the catalog counts in [Card Catalog & Data](04-card-catalog-and-data.md). If you change any of those, update the corresponding table in the same commit.
