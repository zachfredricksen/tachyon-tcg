# Utilities

> **Scope:** every class in `com.osrstcg.util` — what each one does, who calls it, and the non-obvious behaviour hiding in the small ones.
> **Key packages:** `com.osrstcg.util`
> **Related:** [Architecture Overview](01-architecture-overview.md) · [Card Catalog and Data](04-card-catalog-and-data.md) · [Packs and Rarity](08-packs-and-rarity.md) · [Notifications](13-notifications.md) · [Build, Test and Release](16-build-test-and-release.md)

## Purpose

`com.osrstcg.util` is the plugin's leaf layer. Nine classes, 1027 lines, and every
one of them is `public final` with a private constructor and nothing but `static`
members. No class in this package holds mutable per-player state, participates in
Guice injection, or subscribes to the RuneLite event bus — that all lives in
`com.osrstcg.service`. If you find yourself wanting to add an injected field to
something in `util`, the class belongs in `service` instead.

The package splits into three rough groups. **Game-client adapters**
([PetNpcIds](../src/main/java/com/osrstcg/util/PetNpcIds.java),
[PlayerCombatUtil](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java),
[GameWidgetUtil](../src/main/java/com/osrstcg/util/GameWidgetUtil.java)) read the
live `Client` and must run on the client thread. **Text formatters**
([NumberFormatting](../src/main/java/com/osrstcg/util/NumberFormatting.java),
[HtmlEntities](../src/main/java/com/osrstcg/util/HtmlEntities.java),
[TcgPluginGameMessages](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java),
[PullNotificationMessages](../src/main/java/com/osrstcg/util/PullNotificationMessages.java))
turn model data into strings for chat, Discord and Swing. **Bounds helpers**
([PackRevealZoomUtil](../src/main/java/com/osrstcg/util/PackRevealZoomUtil.java),
[CollectionAlbumWindowSizeUtil](../src/main/java/com/osrstcg/util/CollectionAlbumWindowSizeUtil.java))
own the clamp ranges for two persisted UI values.

The bounds helpers matter more than their size suggests. Both zoom and album
window size are round-tripped through the save file, which means a corrupted or
hand-edited profile can put a garbage value into the UI. Centralising the clamp in
`util` lets the codec, the state service and the UI all apply *the same* bounds
without duplicating the constants — see [Clamp helpers](#clamp-helpers-packrevealzoomutil-and-collectionalbumwindowsizeutil).

## Class reference

| Class | Lines | Responsibility |
|---|---|---|
| [PetNpcIds](../src/main/java/com/osrstcg/util/PetNpcIds.java) | 485 | Membership set of follower-pet NPC ids, so pets never count as combat |
| [TcgPluginGameMessages](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java) | 236 | RuneLite chat markup builders + the `[OSRS TCG]` prefix and queue helper |
| [PlayerCombatUtil](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java) | 71 | "Am I in combat?" — outgoing target plus incoming targeting scan |
| [HtmlEntities](../src/main/java/com/osrstcg/util/HtmlEntities.java) | 50 | **Decodes** HTML character references in wiki-sourced catalog strings |
| [PullNotificationMessages](../src/main/java/com/osrstcg/util/PullNotificationMessages.java) | 49 | Plain-text / Markdown bodies for Dink and raw Discord webhooks |
| [CollectionAlbumWindowSizeUtil](../src/main/java/com/osrstcg/util/CollectionAlbumWindowSizeUtil.java) | 43 | Minimum and screen-max bounds for the album `JFrame` |
| [NumberFormatting](../src/main/java/com/osrstcg/util/NumberFormatting.java) | 42 | Thousands grouping with a space separator (`1 234 567`) |
| [GameWidgetUtil](../src/main/java/com/osrstcg/util/GameWidgetUtil.java) | 31 | Detects the post-login welcome screen widget |
| [PackRevealZoomUtil](../src/main/java/com/osrstcg/util/PackRevealZoomUtil.java) | 20 | Clamps the pack-reveal overlay zoom to `[0.35, 2.5]` |

## PetNpcIds — 444 ids and why they exist

`PetNpcIds` is by far the largest file in the package and by far the least
interesting per line. It builds one immutable-by-convention
`Set<Integer>` in a static initialiser
([PetNpcIds.java:15](../src/main/java/com/osrstcg/util/PetNpcIds.java#L15)) and
exposes three membership predicates over it.

The set exists for exactly one reason: **a pet following you is an NPC that is
"interacting with" you every tick.** Without a filter, the incoming-targeting scan
in [PlayerCombatUtil.java:41](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java#L41)
would report every player with a pet out as permanently in combat, which would
permanently block pack opening under Safe mode. The set is the exclusion list that
makes that scan usable.

### Sourcing

The class javadoc states the ids are taken from RuneLite's `net.runelite.api.gameval.NpcID`
constants, and that the set matches the one PetInfoPlugin's `PetJsonCreator`
builds ([PetNpcIds.java:9-12](../src/main/java/com/osrstcg/util/PetNpcIds.java#L9-L12)).
That gives the file two distinct halves:

| Form | Count | Where |
|---|---|---|
| `ids.add(NpcID.CONSTANT)` | 425 | [lines 39–463](../src/main/java/com/osrstcg/util/PetNpcIds.java#L39-L463) |
| `ids.add(<literal>); // comment` | 19 | [lines 464–482](../src/main/java/com/osrstcg/util/PetNpcIds.java#L464-L482) |

444 `ids.add` calls total, with no duplicate literal ids and no duplicate `NpcID`
constant names. The named block is alphabetical by constant name
(`ABYSSALSIRE_PET` … `ZUK_PET`). The literal block at the end is the manual tail:
ids that have no `NpcID` constant in the pinned RuneLite release, each carrying a
hand-written comment naming the creature — the Varrock stray dog at
[line 464](../src/main/java/com/osrstcg/util/PetNpcIds.java#L464) and the
Varlamore dog breeds (Molossus, Xolo, Chiribaya, Techichi) at
[lines 465–482](../src/main/java/com/osrstcg/util/PetNpcIds.java#L465-L482).

Note that many entries are *variant* ids for the same visual pet — `GROWNCAT`,
`GROWNCAT_BLACK`, `GROWNCAT_BROWN`, `GROWNCAT_HELL` and so on
([lines 65–70](../src/main/java/com/osrstcg/util/PetNpcIds.java#L65-L70)), and the
`WILEYCAT_*` block at [lines 454–460](../src/main/java/com/osrstcg/util/PetNpcIds.java#L454-L460).
Recolours and legacy/pre-rework ids (`CALLISTOPET_LEGACY`,
`VETIONPET_2_LEGACY`) each need their own entry, which is why the count is 444 and
not the ~200-odd distinct pets in the game.

### API

```java
public static boolean isPetNpc(int npcId)      // raw id lookup
public static boolean isPetNpc(NPC npc)        // null-safe; false for null
public static boolean isPetActor(Actor actor)  // instanceof NPC + isPetNpc
```

`isPetActor` ([line 31](../src/main/java/com/osrstcg/util/PetNpcIds.java#L31)) is
the widest of the three: it returns `false` for a `Player`, which is the right
answer for the combat check but would be the wrong answer if you ever reused it as
a general "is this thing harmless" predicate.

The only caller in `src/main` is `PlayerCombatUtil`. Keep it that way unless you
have a genuine second consumer — the class is a data blob, and widening its use
widens the blast radius of any id that turns out to be wrong.

## PlayerCombatUtil

`PlayerCombatUtil.isLocalPlayerInCombat(Client)` answers the question the pack
Safe-mode feature depends on. It checks combat in **both directions**, because
either one should block a pack reveal covering your screen.

```
in combat  =  local.getInteracting() is a combat target
           OR any NPC in the top-level WorldView (excluding pets) is interacting with local
           OR any other Player in the top-level WorldView is interacting with local
```

The outgoing check runs first
([PlayerCombatUtil.java:30-34](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java#L30-L34))
and short-circuits, so the two `WorldView` loops only run when you are not
attacking anything. Both loops read
`client.getTopLevelWorldView()` — instance/plane sub-world-views are not scanned.

`isCombatTarget(Actor)` ([line 59](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java#L59))
encodes the asymmetry that trips people up: **any** `Player` counts as a combat
target, but an `NPC` only counts if it is not a pet. There is no equivalent
"friendly player" exclusion — targeting a player at all is treated as combat.

Every guard is null-tolerant: a `null` client, a `null` local player or a `null`
`WorldView` all return `false` rather than throwing
([lines 20-27, 36-38](../src/main/java/com/osrstcg/util/PlayerCombatUtil.java#L20-L27)).
That matters because the caller runs on `GameTick` during login/logout transitions
when the local player can legitimately be absent.

### Caller: PlayerCombatMonitor

The sole consumer is
[PlayerCombatMonitor.java](../src/main/java/com/osrstcg/service/PlayerCombatMonitor.java),
a `@Singleton` that caches the result in a `volatile boolean` so the Swing EDT can
read it without touching the client. The monitor layers a **3-tick grace period**
on top of the pure predicate: `INCOMING_DAMAGE_GRACE_TICKS = 3`, set on
`HitsplatApplied` for any hitsplat on the local player that is not
`isMine()`. During the grace window the monitor reports "in combat" even when
`PlayerCombatUtil` says otherwise
(`localPlayerInCombat = grace || PlayerCombatUtil.isLocalPlayerInCombat(client)`,
[PlayerCombatMonitor.java:46](../src/main/java/com/osrstcg/service/PlayerCombatMonitor.java#L46)).
The grace exists because ranged/magic attackers frequently stop showing as
`interacting` between attacks, so the raw predicate flickers.

Do not "fix" that flicker inside `PlayerCombatUtil`. The utility is deliberately
memoryless; the tick-based smoothing belongs in the monitor, which has a tick
counter and lifecycle.

## GameWidgetUtil

One method, one widget:

```java
WELCOME_SCREEN_GROUP = 378   // WelcomeScreen.UNIVERSE
WELCOME_SCREEN_CHILD = 0
```

`isWelcomeScreenVisible(Client)` returns true when
`client.getWidget(378, 0)` is non-null and not hidden
([GameWidgetUtil.java:28-29](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L28-L29)).
The group/child pair is hardcoded rather than referenced through the generated
`InterfaceID` constants, with the intended constant name recorded in a comment at
[line 13](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L13). If Jagex
renumbers that interface this silently starts returning `false` forever — there is
no logging and no fallback.

The class javadoc is explicit that this **must be called on the client thread**
([lines 6-10](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L6-L10)), and
the one caller obeys: `PackSafeModeService.onGameTick` polls it every tick and
refreshes the sidebar panel only on transitions
([PackSafeModeService.java:116-122](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L116-L122)).
The transition guard is what keeps a per-tick widget poll from becoming a per-tick
Swing repaint.

## NumberFormatting

**There is no abbreviation and no rounding in this class.** If you came here
looking for `12.3K` / `1.5M` style output, it does not exist anywhere in
`src/main/java` — `NumberFormatting` groups thousands and nothing else. The class
javadoc says so directly:
[NumberFormatting.java:3-5](../src/main/java/com/osrstcg/util/NumberFormatting.java#L3-L5).

The separator is a plain ASCII space, not a comma and not a non-breaking space:

```
format(1234567L)  ->  "1 234 567"
format(-1234L)    ->  "-1 234"
format(0)         ->  "0"
format((Long) null) -> "-"
```

The grouping loop walks the digit string left to right and emits a space whenever
the number of remaining digits is a positive multiple of three
([line 34](../src/main/java/com/osrstcg/util/NumberFormatting.java#L34)):

```java
if (i > 0 && ((digits.length() - i) % 3 == 0)) out.append(' ');
```

The sign is stripped up front via `Math.abs` and re-prepended
([lines 29-30, 40](../src/main/java/com/osrstcg/util/NumberFormatting.java#L29-L30)),
so the grouping never counts the `-`. Note the `Math.abs(Long.MIN_VALUE)` trap:
`Math.abs` returns `Long.MIN_VALUE` unchanged for that one input, and the result
would be `"--9 223 372 036 854 775 808"`. No credit value gets anywhere near
that, but it is a real edge case if you ever unit-test the class.

Three overloads exist ([lines 12, 17, 22](../src/main/java/com/osrstcg/util/NumberFormatting.java#L12-L25)):
`long`, boxed `Long` (null becomes `"-"`, the only null handling in the class), and
`int`. The boxed overload is the reason the primitive `long` and `int` overloads
both exist — without them, `null`-vs-zero would be ambiguous at call sites.

Eight files call it, spanning every UI surface: the sidebar
[TcgPanel](../src/main/java/com/osrstcg/ui/TcgPanel.java), the
[CreditsInfoboxOverlay](../src/main/java/com/osrstcg/overlay/CreditsInfoboxOverlay.java),
the [CollectionAlbumWindow](../src/main/java/com/osrstcg/ui/collectionalbum/CollectionAlbumWindow.java),
the [SaveRestoreDialog](../src/main/java/com/osrstcg/ui/save/SaveRestoreDialog.java),
[SharedCardRenderer](../src/main/java/com/osrstcg/ui/SharedCardRenderer.java),
[CreditAwardService](../src/main/java/com/osrstcg/service/CreditAwardService.java),
[TcgChatStatsShareService](../src/main/java/com/osrstcg/service/TcgChatStatsShareService.java)
and [OsrsTcgPlugin](../src/main/java/com/osrstcg/OsrsTcgPlugin.java). That breadth
is the point: one formatter means credits render identically in chat, in the
infobox and in the album.

## HtmlEntities

Read the direction carefully: **`HtmlEntities.decode` decodes, it does not
escape.** There is no `escape` method in this class or anywhere else in
`src/main/java`.

The entry point is `decode(String)`
([HtmlEntities.java:16](../src/main/java/com/osrstcg/util/HtmlEntities.java#L16)).
It fast-paths out when the input is `null`, empty, or contains no `&` at all
([line 18](../src/main/java/com/osrstcg/util/HtmlEntities.java#L18)) — which is
the common case for the overwhelming majority of card names, so the regex work is
rarely reached.

What it handles, in order:

| Step | Handles | Line |
|---|---|---|
| Literal replaces | `&amp;` `&lt;` `&gt;` `&quot;` `&apos;` | [23–27](../src/main/java/com/osrstcg/util/HtmlEntities.java#L23-L27) |
| `DECIMAL_REF` | `&#(\d+);` parsed radix 10 | [9, 28](../src/main/java/com/osrstcg/util/HtmlEntities.java#L9) |
| `HEX_REF` | `&#x([0-9a-fA-F]+);` parsed radix 16 | [10, 29](../src/main/java/com/osrstcg/util/HtmlEntities.java#L10) |

Two things about the ordering. First, `&amp;` is replaced **first**, so an input
of `&amp;lt;` becomes `&lt;` and then `<` — double-decoding. That is the usual
trade-off in a naive entity decoder and is fine for the trusted input this sees.
Second, the numeric replacement casts the parsed code point straight to `char`
([line 44](../src/main/java/com/osrstcg/util/HtmlEntities.java#L44)), so anything
above U+FFFF is silently truncated rather than encoded as a surrogate pair. No OSRS
item name needs astral-plane characters, but a `&#128512;` in a future catalog
would come out as garbage rather than an emoji.

`Matcher.quoteReplacement` wraps the replacement text
([line 44](../src/main/java/com/osrstcg/util/HtmlEntities.java#L44)) — without it,
a decoded `$` or `\` in a card name would be interpreted as a group reference by
`appendReplacement` and throw. That is the single most important line in the file.
No named entity beyond the five listed is supported: `&nbsp;` passes through
untouched.

### Where it is used, and the injection angle

There is exactly one call site, and it is at catalog load:
[CardDatabase.normalize](../src/main/java/com/osrstcg/data/CardDatabase.java#L207)
decodes the card `name` and, when present, the `examine` text, both after
`trim()`. The strings originate from the bundled
`src/main/resources/Card.json`, which is generated from OSRS Wiki data — hence the
entities.

The security-relevant consequence runs the *other* way from what the class name
suggests. Because the plugin decodes entities and never re-escapes, a card name
containing `<` reaches the message builders as a literal `<`. Two sinks matter:

- **Game chat.** `TcgPluginGameMessages` feeds card names into
  `ChatMessageBuilder` and the result goes out as
  `runeLiteFormattedMessage`. RuneLite's chat renderer interprets `<col=...>`
  tags in that string, so an unescaped `<` in a card name is markup, not text.
- **Swing.** No Swing component in `src/main/java` is currently fed an
  `<html>`-prefixed string — a repo-wide search for `<html>` in `src/main/java`
  returns nothing — so the classic `JLabel` HTML-injection sink is not open
  today. It would open the moment someone writes
  `new JLabel("<html>" + card.getName() + "</html>")`.

Both sinks are safe *right now* only because the catalog is a bundled, curated
resource. If card definitions ever become remotely loaded or user-supplied, this
is the class to revisit — and the fix is to add an `escape` counterpart, not to
stop decoding.

## TcgPluginGameMessages

This is the plugin's entire chat presentation layer: 236 lines of
`ChatMessageBuilder` recipes plus the queueing helper. Every in-game message the
plugin emits goes through here, which is why nine files import it.

### The prefix

Every message opens with a bracketed, gold `OSRS TCG` label built by the private
`prefixBuilder()`
([lines 31-39](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L31-L39)):
default-coloured `[`, then `OSRS TCG` in `PREFIX_COLOR`, then default `] `.

```java
DEFAULT_PREFIX_COLOR = new Color(0xC4, 0x94, 0x1A)   // warm gold
CHAT_EMPHASIS_GOLD   = new Color(0xC4, 0x94, 0x1A)   // Godly-tier card names
```

`PREFIX_COLOR` is a **mutable public static field**
([line 20](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L20)),
written by `setPrefixColor(Color)`
([line 41](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L41)),
which null-coalesces back to `DEFAULT_PREFIX_COLOR`. This is the one piece of
mutable state in the whole `util` package, and it is process-global rather than
per-plugin-instance. `OsrsTcgPlugin` sets it in two places — at startup and again
on config change
([OsrsTcgPlugin.java:248](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L248),
[OsrsTcgPlugin.java:361](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L361)) —
from `config.chatPrefixColor()`. Note that `DEFAULT_PREFIX_COLOR` and
`CHAT_EMPHASIS_GOLD` are the same RGB today but are separate constants; they mean
different things and should be allowed to diverge.

### The formatted/plain pair convention

Almost every message exists twice: a `formatPrefixed*` variant that builds
RuneLite colour markup, and a `plainPrefixed*` twin that produces the same
sentence as flat text with a literal `"[OSRS TCG] "` prefix.

| Event | Formatted | Plain |
|---|---|---|
| Someone pulled a card | [`formatPrefixedSomeonePulled`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L74) | [`plainPrefixedSomeonePulled`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L87) |
| Someone added to collection | [`formatPrefixedSomeoneAddedCollection`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L92) | [`plainPrefixedSomeoneAddedCollection`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L108) |
| You pulled | [`formatPrefixedYouPulled`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L115) | [`plainPrefixedYouPulled`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L126) |
| You added to collection | [`formatPrefixedYouAddedCollection`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L131) | [`plainPrefixedYouAddedCollection`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L145) |
| Someone sent you a card | [`formatPrefixedSomeoneSentYou`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L151) | [`plainPrefixedSomeoneSentYou`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L164) |
| You sent a card | [`formatPrefixedYouSentCard`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L169) | [`plainPrefixedYouSentCard`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L184) |

The reason for the pair is
[`queueFormattedGameMessage`](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L189):
it builds a `QueuedMessage` with `runeLiteFormattedMessage(formatted)` **and**
`value(plain)`. RuneLite uses the formatted string for rendering and the plain
`value` for anything that reads the raw message text (chat history, filters,
external consumers). If you add a message type, you must add both halves or the
plain fallback will be empty.

Formatting details worth knowing:

- `announcedCardLabel(cardName, foil)`
  ([line 64](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L64)) is
  the single source of truth for how a card is named in chat: trimmed, no quotes,
  `" (foil)"` appended when foil, and the literal `"Unknown card"` when the name
  trims to empty.
- `duplicatePrefix(newForCollection)`
  ([line 232](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L232))
  returns `""` for a new card and `"duplicate "` otherwise. It is emitted *before*
  the rarity-coloured card name, so the word "duplicate" stays default-coloured.
- `formatPrefixedSomeoneSentYou` and `plainPrefixedSomeoneSentYou` produce
  `"... just sent you X !"` — with a space before the exclamation mark
  ([lines 160, 166](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L160)).
  Every other message has no space. This looks like a typo; it is reproduced in
  both the formatted and plain twin, so at least it is *consistently* wrong.
- `withPrefix(String)` ([line 49](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L49))
  is the generic single-colour path used by `queuePrefixedGameMessage`
  ([line 213](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L213)),
  which composes the plain twin itself as `"[OSRS TCG] " + body`.
- `formatPrefixedNotEnoughCredits(action)`
  ([line 222](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L222))
  is the odd one out — it appends `" Not enough credits to "` with a **leading**
  space after the prefix, which already ends in a space, giving a double space in
  the rendered line. It also has no `plain` twin.

`queueFormattedGameMessage` no-ops on a `null` `ChatMessageManager` and coerces
`null` strings to `""` ([lines 189-202](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L189-L202)),
so message emission never NPEs during teardown. All messages go out as
`ChatMessageType.GAMEMESSAGE`.

## PullNotificationMessages

The Discord-facing counterpart to `TcgPluginGameMessages`: same events, but plain
text and Markdown instead of RuneLite colour tags. Two consumers:
[DinkNotificationService](../src/main/java/com/osrstcg/service/DinkNotificationService.java)
and
[PullWebhookNotificationService](../src/main/java/com/osrstcg/service/PullWebhookNotificationService.java).

`collectionMessage(playerName, cardName, newForCollection, foil)`
([line 11](../src/main/java/com/osrstcg/util/PullNotificationMessages.java#L11))
builds:

```
<who> just added [duplicate ]<card>[ (foil)] to their collection!
```

`who` falls back to the literal `"Unknown player"` when the name is null or
blank after trimming
([line 13](../src/main/java/com/osrstcg/util/PullNotificationMessages.java#L13)).
Unlike `announcedCardLabel`, a blank *card* name is not substituted — it just
produces an empty slot in the sentence.

The Dink integration reuses that same builder with a sentinel:
`dinkCollectionMessage` passes the literal string `"%USERNAME%"` as the player
name ([line 22](../src/main/java/com/osrstcg/util/PullNotificationMessages.java#L22)).
Dink expands `%USERNAME%` itself at send time, so the plugin never has to resolve
the display name for that path. The raw-webhook path
([PullWebhookNotificationService.java:95](../src/main/java/com/osrstcg/service/PullWebhookNotificationService.java#L95))
has no such expander and calls `collectionMessage` with a real resolved name.

`dinkPackSummaryMessage(newCards, duplicates)`
([line 25](../src/main/java/com/osrstcg/util/PullNotificationMessages.java#L25))
emits Discord Markdown:

```
%USERNAME% opened a booster pack!

**New cards**
- Card A
- Card B

**Duplicates**
- None
```

`markdownCardList` ([line 32](../src/main/java/com/osrstcg/util/PullNotificationMessages.java#L32))
renders `"- None"` for a null or empty list, so both headings are always followed
by content. Card names are inserted verbatim — a card name containing Markdown
control characters would be interpreted by Discord. Same trust assumption as
`HtmlEntities`: fine while the catalog is bundled.

## Clamp helpers: PackRevealZoomUtil and CollectionAlbumWindowSizeUtil

Both classes exist so that the same bounds are enforced at every layer that can
introduce a bad value.

### PackRevealZoomUtil

```java
MIN = 0.35d
MAX = 2.5d
clamp(NaN)      -> 1.0
clamp(Infinity) -> 1.0
clamp(v)        -> Math.max(0.35, Math.min(2.5, v))
```

The NaN/Infinity guard returns `1.0`, not `MIN`
([PackRevealZoomUtil.java:14-17](../src/main/java/com/osrstcg/util/PackRevealZoomUtil.java#L14-L17)).
That is deliberate: a non-finite value means the stored data was corrupt, and the
sane recovery is "neutral zoom", not "smallest allowed zoom". Note that
`Math.max(MIN, Math.min(MAX, NaN))` would return `NaN` without the guard, since
`Math.min`/`Math.max` propagate NaN — the explicit check is load-bearing.

The clamp is applied in four places, which is the interesting part:

| Layer | Call site | Why |
|---|---|---|
| Model constructor | [TcgState.java:35](../src/main/java/com/osrstcg/model/TcgState.java#L35) | No `TcgState` can ever hold an out-of-range zoom |
| Persistence | [TcgStateCodec.java:80](../src/main/java/com/osrstcg/persist/TcgStateCodec.java#L80) | Sanitises a hand-edited or corrupt save on read |
| State service | [TcgStateService.java:462](../src/main/java/com/osrstcg/service/TcgStateService.java#L462) | Clamps the value coming from the UI before storing |
| Overlay | [PackRevealOverlay.java:572, 592, 1107](../src/main/java/com/osrstcg/overlay/PackRevealOverlay.java#L572) | Clamps the live session multiplier during scroll-zoom |

The overlay also re-exports the bounds as its own package-private constants
`PACK_REVEAL_ZOOM_MIN` / `PACK_REVEAL_ZOOM_MAX`
([PackRevealOverlay.java:59-60](../src/main/java/com/osrstcg/overlay/PackRevealOverlay.java#L59-L60))
rather than redefining them — follow that pattern if you need the numbers
elsewhere.

### CollectionAlbumWindowSizeUtil

```java
MIN_WIDTH  = 1300
MIN_HEIGHT = 810
```

Three methods
([CollectionAlbumWindowSizeUtil.java:17-42](../src/main/java/com/osrstcg/util/CollectionAlbumWindowSizeUtil.java#L17-L42)):

- `isStoredSize(w, h)` — true only when both are strictly positive. `0` is the
  "never saved" sentinel.
- `resolve(storedW, storedH)` — returns the minimum size when nothing is stored,
  otherwise delegates to `clamp`.
- `clamp(w, h)` — raises to the minimum, then lowers to
  `GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()`.

The ordering matters and is easy to get backwards: **minimum first, screen
maximum second**
([lines 32-40](../src/main/java/com/osrstcg/util/CollectionAlbumWindowSizeUtil.java#L32-L40)).
On a display smaller than 1300×810 the screen bound wins, and the window comes out
*below* `MIN_WIDTH`. That is intentional — an album larger than the screen is worse
than a cramped one — but it means `clamp` does not guarantee its own minimums. Any
code that assumes `clamp(...).width >= MIN_WIDTH` is wrong.

`getMaximumWindowBounds()` excludes OS taskbars/docks, so the album will not hide
behind them. The `null` check on the returned rectangle
([line 37](../src/main/java/com/osrstcg/util/CollectionAlbumWindowSizeUtil.java#L37))
is defensive against headless/odd environments.

Callers: [TcgStateService.java:473](../src/main/java/com/osrstcg/service/TcgStateService.java#L473)
clamps before persisting, and
[CollectionAlbumWindow](../src/main/java/com/osrstcg/ui/collectionalbum/CollectionAlbumWindow.java#L849)
calls `resolve` when sizing the frame and uses the raw `MIN_*` constants for
`setMinimumSize` ([lines 206-207](../src/main/java/com/osrstcg/ui/collectionalbum/CollectionAlbumWindow.java#L206-L207)).

## Testability

Nothing in this package is currently covered by a test — the only two real test
classes live in `com.osrstcg.persist` (see
[Build, Test and Release](16-build-test-and-release.md)). That is a shame, because
this is the easiest package in the repo to test: every class is `final`, every
method is `static`, and most have no dependencies at all.

| Class | Test difficulty | Notes |
|---|---|---|
| `NumberFormatting` | Trivial | Pure, no imports beyond the JDK. Cover negatives, zero, boxed null, `Long.MIN_VALUE` |
| `PackRevealZoomUtil` | Trivial | Pure. Cover NaN, ±Infinity, below-min, above-max |
| `HtmlEntities` | Trivial | Pure. Cover double-decode of `&amp;lt;`, `$`/`\` in replacements, unknown `&nbsp;` |
| `PullNotificationMessages` | Trivial | Pure string building. Cover null/blank player, empty lists |
| `PetNpcIds` | Easy | `isPetNpc(int)` needs no client; the `NPC` overload needs a one-line stub or mock |
| `CollectionAlbumWindowSizeUtil` | Easy, with a caveat | `isStoredSize` is pure; `clamp`/`resolve` touch `GraphicsEnvironment`, so they need a non-headless JVM or a seam |
| `TcgPluginGameMessages` | Moderate | The `plainPrefixed*` methods are pure strings and testable as-is; the `formatPrefixed*` methods need RuneLite's `ChatMessageBuilder` on the classpath (it is — via `testImplementation`) |
| `PlayerCombatUtil` | Moderate | Needs a mocked `Client`/`WorldView`; no mocking library is currently on the test classpath |
| `GameWidgetUtil` | Moderate | Needs a mocked `Client` returning a `Widget` |

The four "trivial" rows plus `PetNpcIds.isPetNpc(int)` are the highest
value-per-effort tests in the entire codebase: pure functions, real edge cases
(NaN, `Long.MIN_VALUE`, regex replacement escaping) and zero setup.

## Data flow

The most instructive path through this package is a single card pull, which
touches five of the nine classes.

```
CardDatabase.normalize()                       [load time, once]
   └── HtmlEntities.decode(name / examine)     -> clean catalog strings
                    │
                    ▼
PackSafeModeService.onGameTick()               [client thread, every tick]
   ├── GameWidgetUtil.isWelcomeScreenVisible(client)
   └── PlayerCombatMonitor.isLocalPlayerInCombat()
           └── PlayerCombatUtil.isLocalPlayerInCombat(client)
                   └── PetNpcIds.isPetNpc(npc)  -> pets excluded
                    │
                    ▼  (pack allowed to open)
PullNotificationService                        [client thread]
   ├── TcgPluginGameMessages.formatPrefixedYouPulled(name, foil, rarityColor)
   ├── TcgPluginGameMessages.plainPrefixedYouPulled(name, foil)
   └── TcgPluginGameMessages.queueFormattedGameMessage(mgr, formatted, plain)
                    │
                    ▼
DinkNotificationService / PullWebhookNotificationService
   └── PullNotificationMessages.collectionMessage(...)   -> Discord body
                    │
                    ▼
TcgPanel / CollectionAlbumWindow               [Swing EDT]
   ├── NumberFormatting.format(credits)
   └── CollectionAlbumWindowSizeUtil.resolve(storedW, storedH)
```

Notice that `HtmlEntities` runs once at catalog load, while everything downstream
consumes its output. That is why there is no re-decoding anywhere else: the
catalog is normalised exactly once, at the boundary.

## Threading

The package has no threading logic of its own — nothing here spawns a thread,
schedules work, or synchronises. But three classes have a hard thread requirement
imposed by what they read:

| Class | Required thread | Reason |
|---|---|---|
| `PlayerCombatUtil` | **Client thread** | Reads `Client.getLocalPlayer()`, `getTopLevelWorldView()`, and iterates live actor lists |
| `GameWidgetUtil` | **Client thread** | `Client.getWidget` is not safe off-thread; documented at [GameWidgetUtil.java:8-9](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L8-L9) |
| `PetNpcIds` | Client thread for the `NPC`/`Actor` overloads | `NPC.getId()` reads live client state. `isPetNpc(int)` is thread-free |
| `CollectionAlbumWindowSizeUtil` | **Swing EDT** in practice | `GraphicsEnvironment` is not tied to the EDT, but every caller is a Swing sizing path |
| Everything else | Any | Pure string/number functions |

`TcgPluginGameMessages.PREFIX_COLOR` is the one cross-thread hazard: it is a
non-volatile mutable static written from the plugin's config-change handler and
read from any thread that formats a message. In practice both the write and the
reads happen on the client thread, so there is no observed problem — but the field
is not declared `volatile`, so the visibility guarantee is incidental rather than
designed.

## Gotchas & invariants

**`HtmlEntities` decodes; it never escapes.** After catalog load, card names may
contain raw `<`, `>`, `&` and `"`. Anything that renders them into a markup
context (RuneLite chat colour tags, Swing `<html>` labels, Discord Markdown)
inherits that. Adding a Swing `JLabel("<html>" + cardName + ...)` opens an
injection path that does not exist today.

**`NumberFormatting` does not abbreviate.** Do not document, assume, or add
abbreviation behaviour to it without checking every one of its eight call sites —
the album and infobox layouts are sized around the space-grouped form.

**`CollectionAlbumWindowSizeUtil.clamp` can return less than `MIN_WIDTH`.** The
screen bound is applied after the minimum
([lines 35-40](../src/main/java/com/osrstcg/util/CollectionAlbumWindowSizeUtil.java#L35-L40)).
Never assert the minimums on its output.

**`PackRevealZoomUtil.clamp` must keep its explicit NaN/Infinity branch.** Folding
it into the `Math.max`/`Math.min` chain would let `NaN` propagate straight into the
overlay transform.

**`PREFIX_COLOR` is global and mutable.** It survives plugin shutdown because it
is a `static` on a class the classloader keeps. If the plugin is disabled and
re-enabled with a different config, the colour is only correct because
`OsrsTcgPlugin` re-applies it on startup
([OsrsTcgPlugin.java:248](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L248)).

**Every `formatPrefixed*` needs a `plainPrefixed*` twin.** `queueFormattedGameMessage`
takes both, and the plain value is what non-rendering consumers read. A missing
twin shows up as an empty chat line in transcripts, not as an exception.

**Pets are excluded only in the NPC direction.** `PlayerCombatUtil.isCombatTarget`
returns `true` for any `Player`, with no pet-equivalent filter. There is no such
thing as a "harmless player" in this model.

**`PetNpcIds` is a snapshot, not a live query.** New pets added by Jagex are
invisible until someone adds the id. Symptom of a missing id: a player with that
pet out can never open a pack under Safe mode, with no error message explaining
why. Add new ids as `NpcID.` constants when available, and as commented literals
in the tail block
([lines 464–482](../src/main/java/com/osrstcg/util/PetNpcIds.java#L464-L482))
when not.

**The welcome-screen widget id is hardcoded.** `378`/`0`
([GameWidgetUtil.java:14-15](../src/main/java/com/osrstcg/util/GameWidgetUtil.java#L14-L15)).
If it changes, detection fails silently — `getWidget` returns `null` and the method
returns `false` forever.

### Open questions

- The `HashSet` in `PetNpcIds` is constructed with an initial capacity of `444`
  ([line 38](../src/main/java/com/osrstcg/util/PetNpcIds.java#L38)), matching the
  444 `ids.add` calls exactly. With the default 0.75 load factor the table will
  resize once at 333 entries, so the hint does not actually avoid a rehash. It is
  unclear whether `444` was intended as the element count (it matches) or as a
  capacity that avoids resizing (it does not). Harmless either way — this runs once
  at class load.
- `formatPrefixedNotEnoughCredits`
  ([line 222](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L222))
  is the only `format*` method with no `plain*` twin and the only one whose body
  starts with a space. Whether the double space is intentional could not be
  determined from the code.
- The space before `!` in the "sent you" messages
  ([lines 160, 166](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L160))
  is inconsistent with every other message. It is duplicated in the plain twin, so
  it may be deliberate — the commit history does not say.
