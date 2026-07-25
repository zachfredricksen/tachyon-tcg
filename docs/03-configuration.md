# Configuration

> **Scope:** the `osrstcg` config group — every `@ConfigItem` in `OsrsTcgConfig`, its default, and the class that actually reads it.
> **Key packages:** `com.osrstcg`, `com.osrstcg.model`
> **Related:** [Plugin Lifecycle](02-plugin-lifecycle.md)

## Purpose

`OsrsTcgConfig` ([OsrsTcgConfig.java](../src/main/java/com/osrstcg/OsrsTcgConfig.java)) is a
RuneLite `Config` interface: an annotated interface with no implementation. RuneLite builds
a dynamic proxy for it at
[OsrsTcgPlugin.java:992-996](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L992-L996) and
every getter call on that proxy is a **live read** against `ConfigManager`. There is no
snapshot and no cache, which is why consumers can hold an `OsrsTcgConfig` field and re-read
it inside a paint loop or an event handler with no invalidation logic at all.

The group name is `osrstcg`
([OsrsTcgConfig.java:11](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L11)). That same
group is used by the persistence layer for its state blobs
([TcgStateStore.java:17-22](../src/main/java/com/osrstcg/persist/TcgStateStore.java#L17-L22)),
so the group contains both user-facing settings and machine-written state. Only the 24 keys
below are declared as `@ConfigItem`s and appear in the settings panel.

Two important framing points before the tables. First, **most keys are polled, not
reactive**: the plugin's `onConfigChanged` handler reacts to exactly three keys, and every
other setting simply takes effect the next time its consumer happens to read it. Second,
**this file has no logic**. Every default is a Java `default` method body, and every
interpretation of a value (null-handling, tier comparison, trimming) lives in the consuming
class — so the "Consumed by" column below is the only reliable place to learn what a key
actually does.

## Class reference

| Class | Lines | Responsibility |
|---|---|---|
| [`OsrsTcgConfig`](../src/main/java/com/osrstcg/OsrsTcgConfig.java) | ~338 | The `@ConfigGroup("osrstcg")` interface: 4 sections, 24 items |
| [`PullNotifyTier`](../src/main/java/com/osrstcg/model/PullNotifyTier.java) | ~38 | Enum used by three keys; wraps `RarityMath.Tier` |
| [`DinkNotificationTrigger`](../src/main/java/com/osrstcg/model/DinkNotificationTrigger.java) | ~20 | Enum used by `dinkNotificationTrigger` |

## Sections

RuneLite groups items in the settings panel by `@ConfigSection`. There are four, ordered by
`position`:

| Section field | UI name | `position` | Items |
|---|---|---|---|
| `generalSection = "general"` [:14-19](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L14-L19) | General | 0 | 10 |
| `pullNotificationsSection = "pullNotifications"` [:142-147](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L142-L147) | Pull notifications | 10 | 6 |
| `dinkSection = "dink"` [:221-226](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L221-L226) | Dink | 20 | 6 |
| `webAlbumSection = "webAlbum"` [:300-305](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L300-L305) | Web album | 30 | 2 |

Positions are spaced by 10 so a new section can be inserted without renumbering. Item
positions within each section are contiguous from 0.

## General

Ten items covering the on-screen infobox, sounds, the pack-reveal overlay, combat safety,
and chat presentation. Note that only `chatPrefixColor` is reactive; the rest are read on
demand by their consumers — the infobox reads `creditsInfobox` on every render pass, the
overlay reads its two rarity keys on every card draw.

| Key | UI name | Type | Default | Consumed by |
|---|---|---|---|---|
| `creditsInfobox` | Credits infobox | `boolean` | `false` | [`CreditsInfoboxOverlay`:60](../src/main/java/com/osrstcg/overlay/CreditsInfoboxOverlay.java#L60) — early-returns from `render` when off |
| `creditsPerHour` | Credits per hour | `boolean` | `true` | [`CreditsInfoboxOverlay`:78](../src/main/java/com/osrstcg/overlay/CreditsInfoboxOverlay.java#L78) — appends the credits/h line |
| `shopNotifications` | Shop notifications | `boolean` | `true` | [`ShopNotificationService`:40](../src/main/java/com/osrstcg/service/ShopNotificationService.java#L40) — gates the affordability chat |
| `enableSounds` | Enable pack opening sounds | `boolean` | `true` | [`PackRevealSoundService`:55, 72, 85, 111, 127](../src/main/java/com/osrstcg/service/PackRevealSoundService.java#L55) — hum, reveal, flip, apex hover, deal-phase loop |
| `enableTransferSound` | Enable transfer sound | `boolean` | `true` | [`PackRevealSoundService`:98](../src/main/java/com/osrstcg/service/PackRevealSoundService.java#L98) — trade-complete cue only |
| `packRarityHighlight` | Rarity Highlight | `boolean` | `true` | [`PackRevealOverlay`:188, 242](../src/main/java/com/osrstcg/overlay/PackRevealOverlay.java#L188) |
| `packRarityText` | Rarity Text | `boolean` | `false` | [`PackRevealOverlay`:278](../src/main/java/com/osrstcg/overlay/PackRevealOverlay.java#L278) |
| `safeMode` | Safe-mode | `boolean` | `false` | [`PackSafeModeService`:61, 71, 95, 106, 124](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L61) |
| `chatPrefixColor` | Chat prefix colour | `java.awt.Color` | `new Color(0xC4, 0x94, 0x1A)` | [`OsrsTcgPlugin`:248, 361](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L248) → `TcgPluginGameMessages.setPrefixColor` |
| `debugMessages` | Debug messages | `boolean` | `false` | [`TcgStateService`:438](../src/main/java/com/osrstcg/service/TcgStateService.java#L438) (`isDebugChatEnabled()`), [`CollectionShareService`:757](../src/main/java/com/osrstcg/service/CollectionShareService.java#L757) |

A few of these need more than a table row.

**`packRarityHighlight` / `packRarityText`** are the accessibility pair. The highlight is a
colour wash; the text label spells the rarity out. The overlay condition at
[PackRevealOverlay.java:242](../src/main/java/com/osrstcg/overlay/PackRevealOverlay.java#L242)
is `config.packRarityHighlight() || (faceUp && !config.packRarityHighlight())` — i.e. a
face-up card is always drawn with its rarity treatment, and the toggle only controls
face-*down* hover. `packRarityText` additionally requires `lift > 0.001d`
([:278](../src/main/java/com/osrstcg/overlay/PackRevealOverlay.java#L278)), so the label
only appears once the card has actually lifted under the cursor. The item description calls
out the colour-blind use case explicitly
([OsrsTcgConfig.java:96-97](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L96-L97)).

**`safeMode`** is checked five separate times in `PackSafeModeService` because it guards
several distinct decisions: whether the feature is on at all
([:61](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L61)), whether opening
is currently blocked
([:71](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L71)), and three
event-handler fast paths ([:95](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L95),
[:106](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L106),
[:124](../src/main/java/com/osrstcg/service/PackSafeModeService.java#L124)). Each gate is
`config.safeMode() && combatMonitor.isLocalPlayerInCombat()` — the config alone never blocks
anything.

**`chatPrefixColor`** is the only key whose value is copied into mutable static state:
`TcgPluginGameMessages.PREFIX_COLOR`
([TcgPluginGameMessages.java:20](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L20)).
`setPrefixColor(null)` falls back to `DEFAULT_PREFIX_COLOR`, the same `0xC4941A` gold as the
config default ([TcgPluginGameMessages.java:18](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L18),
[:41-44](../src/main/java/com/osrstcg/util/TcgPluginGameMessages.java#L41-L44)). Because it
is static, it is process-wide and is never restored on plugin shutdown.

**`debugMessages` is not the debug mode that gates `::tcg-*` commands.** Those check
`TcgStateService.isDebugLogging()`, which reads persisted `TcgState`, not config. The two
are deliberately separated at
[TcgStateService.java:424-439](../src/main/java/com/osrstcg/service/TcgStateService.java#L424-L439):

```java
public boolean isDebugLogging()        { return state.isDebugLogging(); }
public boolean isDebugTracingActive()  { return state.isDebugLogging(); }
public boolean isDebugChatEnabled()    { return config != null && config.get().debugMessages(); }
```

Only `isDebugChatEnabled()` reads this config item. `CollectionShareService` reads it
directly for its own web-album trace lines
([:757](../src/main/java/com/osrstcg/service/CollectionShareService.java#L757)).

## Pull notifications

These six keys control what a pack pull does *outside* the reveal overlay: in-game chat,
the Discord webhook, and the RuneLite party broadcast. All are polled at read time by
`PullNotificationService`, which is called once per revealed card.

| Key | UI name | Type | Default | Consumed by |
|---|---|---|---|---|
| `notifyTier` | Notify tier | `PullNotifyTier` | `MYTHIC` | [`PullNotificationService`:61, 70](../src/main/java/com/osrstcg/service/PullNotificationService.java#L61) |
| `notifyNonFoils` | Notify non-foils | `boolean` | `true` | [`PullNotificationService`:66](../src/main/java/com/osrstcg/service/PullNotificationService.java#L66) |
| `notifyFoils` | Notify all foils | `boolean` | `true` | [`PullNotificationService`:51, 59, 64](../src/main/java/com/osrstcg/service/PullNotificationService.java#L51) |
| `notifyNewCardsOnly` | Only notify new cards | `boolean` | `true` | [`PullNotificationService`:51](../src/main/java/com/osrstcg/service/PullNotificationService.java#L51) |
| `partyAnnounceMythicPulls` | Party collection announcements | `boolean` | `true` | [`PullNotificationService`:115](../src/main/java/com/osrstcg/service/PullNotificationService.java#L115), [`TcgPartyAnnouncer`:85](../src/main/java/com/osrstcg/service/TcgPartyAnnouncer.java#L85), [`OsrsTcgPlugin`:368, 402](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L368) |
| `pullWebhookUrl` | Webhook URL | `String` | `""` | [`PullWebhookNotificationService`:71](../src/main/java/com/osrstcg/service/PullWebhookNotificationService.java#L71), [`PullNotificationService`:182](../src/main/java/com/osrstcg/service/PullNotificationService.java#L182) |

`PullNotifyTier` ([PullNotifyTier.java](../src/main/java/com/osrstcg/model/PullNotifyTier.java))
has seven constants in ascending order — `COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC,
GODLY` — each wrapping a `RarityMath.Tier`. Comparisons are done on `ordinal()`, so
"this tier and higher" is literally `tier.ordinal() >= floor.ordinal()`. A `null` selection
is coerced to `MYTHIC` at both call sites
([:62](../src/main/java/com/osrstcg/service/PullNotificationService.java#L62),
[:71](../src/main/java/com/osrstcg/service/PullNotificationService.java#L71)).

The four notify flags combine in `shouldNotify`
([PullNotificationService.java:49-73](../src/main/java/com/osrstcg/service/PullNotificationService.java#L49-L73)),
and the interaction is not obvious:

```
if (notifyNewCardsOnly && !isNew && !(foil && notifyFoils))   → false
if (foil):
    if (tier == null)                                          → notifyFoils
    return tier >= notifyTier  ||  notifyFoils
if (tier == null || !notifyNonFoils)                           → false
return tier >= notifyTier
```

Two consequences worth internalising. `notifyFoils` is an **override, not a filter**: when
it is on, foils bypass both the tier floor and the new-cards-only gate. And `notifyTier`
does nothing to foils while `notifyFoils` is on, because the `||` short-circuits — to make
the tier floor apply to foils you must turn `notifyFoils` off.

`partyAnnounceMythicPulls` is the broadest key in the file: it gates outbound pull
broadcasts ([PullNotificationService.java:113-130](../src/main/java/com/osrstcg/service/PullNotificationService.java#L113-L130)),
everything `TcgPartyAnnouncer` sends
([:85](../src/main/java/com/osrstcg/service/TcgPartyAnnouncer.java#L85)), **and** inbound
rendering of other players' pull and set-completion messages
([OsrsTcgPlugin.java:368](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L368),
[:402](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L402)). Turning it off silences both
directions. It does **not** gate `TcgChatStatsPartyMessage`, which is always ingested
([OsrsTcgPlugin.java:428-436](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L428-L436)) —
that is the `!tcg` lookup cache, not a pull announcement.

Note that the outbound party broadcast only fires when the *standard* notification passed,
not merely when the config is on: `notifyParty` is called inside the
`if (standardNotification)` block at
[PullNotificationService.java:97-101](../src/main/java/com/osrstcg/service/PullNotificationService.java#L97-L101).
The same is true of the webhook. So the four notify flags are effectively upstream filters
for chat, webhook and party alike.

`pullWebhookUrl` is empty by default, which disables the webhook. Emptiness is tested with
`url != null && !url.trim().isEmpty()`
([:180-184](../src/main/java/com/osrstcg/service/PullNotificationService.java#L180-L184)), so
whitespace counts as unset. There is no scheme validation here.

## Dink

Dink is a separate community plugin that relays notifications to Discord. These six keys
form a **second, independent** filter chain — they do not read any of the `notify*` keys
above, and a card can satisfy one chain without the other.

| Key | UI name | Type | Default | Consumed by |
|---|---|---|---|---|
| `dinkNotifications` | Enable Dink Notifications | `boolean` | `false` | [`PullNotificationService`:91, 108, 134](../src/main/java/com/osrstcg/service/PullNotificationService.java#L91) |
| `dinkNotificationTrigger` | Trigger notification | `DinkNotificationTrigger` | `EVERY_CARD` | [`PullNotificationService`:176](../src/main/java/com/osrstcg/service/PullNotificationService.java#L176) |
| `dinkNewCardNotifyTier` | Notify tier | `PullNotifyTier` | `MYTHIC` | [`PullNotificationService`:168](../src/main/java/com/osrstcg/service/PullNotificationService.java#L168) |
| `dinkAlwaysNotifyFoils` | Notify all foils | `boolean` | `true` | [`PullNotificationService`:159](../src/main/java/com/osrstcg/service/PullNotificationService.java#L159) |
| `dinkOnlyNotifyNew` | Only notify new cards | `boolean` | `true` | [`PullNotificationService`:155](../src/main/java/com/osrstcg/service/PullNotificationService.java#L155) |
| `dinkDuplicateNotifyTier` | Duplicate notify tier | `PullNotifyTier` | `MYTHIC` | [`PullNotificationService`:169](../src/main/java/com/osrstcg/service/PullNotificationService.java#L169) |

`DinkNotificationTrigger`
([DinkNotificationTrigger.java](../src/main/java/com/osrstcg/model/DinkNotificationTrigger.java))
has two constants: `EVERY_CARD` ("Every card") and `AT_END` ("At end"). They select between
two mutually exclusive dispatch paths:

- `EVERY_CARD` → `notifyPull` fires `DinkNotificationService.notifyPackPull` per card
  ([:91-95](../src/main/java/com/osrstcg/service/PullNotificationService.java#L91-L95))
- `AT_END` → `notifyDinkAtEnd` collects every eligible pull in the pack and fires one
  `notifyPackSummary`
  ([:132-151](../src/main/java/com/osrstcg/service/PullNotificationService.java#L132-L151))

A `null` trigger is coerced to `EVERY_CARD`
([:174-178](../src/main/java/com/osrstcg/service/PullNotificationService.java#L174-L178)).

The eligibility predicate is `shouldNotifyDink`
([:153-172](../src/main/java/com/osrstcg/service/PullNotificationService.java#L153-L172)) and
is shared by both paths:

```
if (!isNew && dinkOnlyNotifyNew)      → false
if (foil && dinkAlwaysNotifyFoils)    → true
if (tier == null)                     → false
floor = isNew ? dinkNewCardNotifyTier : dinkDuplicateNotifyTier
return tier >= floor
```

The two-tier split is the point of this chain: `dinkNewCardNotifyTier` applies to cards you
have never owned, `dinkDuplicateNotifyTier` to duplicates. `dinkDuplicateNotifyTier` is
**unreachable while `dinkOnlyNotifyNew` is on**, because duplicates are rejected on the
first line — the item's own description says as much
([OsrsTcgConfig.java:291](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L291)). Both
default to `MYTHIC`, and a `null` tier selection falls back to `MYTHIC`
([:170](../src/main/java/com/osrstcg/service/PullNotificationService.java#L170)).

## Web album

Two keys controlling the optional upload of your collection to `osrs-tcg.xyz`. Unlike every
other section, **both keys are reactive** — see [Reactive vs polled](#reactive-vs-polled-keys).

| Key | UI name | Type | Default | Consumed by |
|---|---|---|---|---|
| `webShareEnabled` | Share collection online | `boolean` | `false` | [`CollectionShareService`:133, 182, 247, 295, 335, 413](../src/main/java/com/osrstcg/service/CollectionShareService.java#L133), [`OsrsTcgPlugin`:354](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L354) |
| `webShareApiKey` | API key | `String` | `""` | [`CollectionShareService`:345](../src/main/java/com/osrstcg/service/CollectionShareService.java#L345) (via `configuredApiKey()`), [`OsrsTcgPlugin`:354](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L354) |

`webShareEnabled` is re-checked at every stage of the sync pipeline rather than latched
once: on service start ([:133](../src/main/java/com/osrstcg/service/CollectionShareService.java#L133)),
on config change ([:182](../src/main/java/com/osrstcg/service/CollectionShareService.java#L182)),
on the keepalive tick ([:247](../src/main/java/com/osrstcg/service/CollectionShareService.java#L247)),
in `canAttemptSync()` ([:335](../src/main/java/com/osrstcg/service/CollectionShareService.java#L335)),
and again at the top of `runSyncPipeline()`
([:413](../src/main/java/com/osrstcg/service/CollectionShareService.java#L413)). That last
one matters: the sync runs on a scheduled executor after a debounce delay, so the user can
disable sharing between scheduling and execution. **If you add a new upload path, re-check
the flag at the point of the network call, not at the point of scheduling.**

`webShareApiKey` is always read through `configuredApiKey()`
([:343-351](../src/main/java/com/osrstcg/service/CollectionShareService.java#L343-L351)),
which null-guards and trims. The service tracks a `rejectedApiKey` so a server-side 401 does
not turn into a retry storm: `isApiKeyBlocked()` compares the current trimmed key against
the rejected one ([:353-357](../src/main/java/com/osrstcg/service/CollectionShareService.java#L353-L357)),
and `clearApiKeyRejectionIfKeyChanged()` unblocks as soon as you type a different key
([:359-371](../src/main/java/com/osrstcg/service/CollectionShareService.java#L359-L371)).
That clear runs *first* inside `onConfigChanged` — before any of the disabled/no-key/blocked
branches — which is what makes pasting a corrected key take effect immediately.

`webShareApiKey` is declared with `secret = true`
([OsrsTcgConfig.java:332](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L332)), which tells
RuneLite's settings panel to treat the value as sensitive rather than rendering it as plain
text.

### The `webShareEnabled` privacy warning

`WEB_SHARE_ENABLED_WARNING` is a `String` constant on the interface
([OsrsTcgConfig.java:307-311](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L307-L311)),
attached to the item via `warning = WEB_SHARE_ENABLED_WARNING`
([:319](../src/main/java/com/osrstcg/OsrsTcgConfig.java#L319)):

```
Enabling this uploads your OSRS TCG collection, collection statistics and
IP address to a third-party server not controlled or verified by RuneLite developers.

Your collection will be publicly viewable under your display name and
will remain visible for a period even after you disable this feature.
```

RuneLite's `@ConfigItem(warning = …)` makes the settings panel show a modal confirmation the
first time the user switches the toggle **on**; declining leaves the value `false`. Turning
it off is not gated.

The reason it exists is the RuneLite plugin-hub policy on third-party data transmission. A
hub plugin that sends user data to a server outside RuneLite's control must obtain informed,
explicit consent through this mechanism — a description string is not sufficient, because
descriptions are passive text that a user can toggle past without reading. The `warning`
field is the only way to force an interstitial. Note what the text is careful to enumerate,
each of which is a separate disclosure obligation:

| Claim in the warning | Backed by |
|---|---|
| collection + statistics are uploaded | `CollectionShareService` sync pipeline; stats come from `TcgPublicStatsCalculator` |
| IP address is exposed | Unavoidable consequence of the plugin making an outbound HTTP request from the user's machine |
| server is not controlled or verified by RuneLite | `osrs-tcg.xyz` is this project's own host |
| publicly viewable under your display name | The upload is keyed on the local player's display name |
| remains visible for a period after disabling | Server-side retention; the plugin has no delete-on-disable call |

Two rules follow for anyone editing this area. **Do not remove or soften this constant** —
doing so will fail plugin-hub review. And **if you add any new outbound transmission of user
data, it needs its own `warning`-gated opt-in**, not a piggyback on `webShareEnabled`.

## Reactive vs polled keys

`onConfigChanged` ([OsrsTcgPlugin.java:347-363](../src/main/java/com/osrstcg/OsrsTcgPlugin.java#L347-L363))
first filters on the group and then on exactly three key names:

```java
if (event == null || !"osrstcg".equals(event.getGroup())) { return; }
if ("webShareEnabled".equals(event.getKey()) || "webShareApiKey".equals(event.getKey()))
{
    collectionShareService.onConfigChanged();
    tcgPanel.updateWebShareLiveIndicator();
}
else if ("chatPrefixColor".equals(event.getKey()))
{
    TcgPluginGameMessages.setPrefixColor(config.chatPrefixColor());
}
```

| Key | Handling | Why it must be reactive |
|---|---|---|
| `webShareEnabled` | Reactive | The share service holds scheduler futures and an indicator state machine; flipping the toggle must cancel a pending debounce and re-derive the status text immediately ([CollectionShareService.java:173-211](../src/main/java/com/osrstcg/service/CollectionShareService.java#L173-L211)) |
| `webShareApiKey` | Reactive | A new key must clear the `rejectedApiKey` block and re-arm a sync; polling would leave the service stuck in `INVALID_API_KEY_STATUS` until the next keepalive |
| `chatPrefixColor` | Reactive | The value lives in static `TcgPluginGameMessages.PREFIX_COLOR`; nothing else ever reads the config item, so without this push the colour would only update on plugin restart |
| **all other 21 keys** | Polled at read time | Their consumers call the getter on every render frame or every event, so a change is picked up on the next natural read |

The group filter is load-bearing for a non-obvious reason: `TcgStateStore` writes its
serialised state into the same `osrstcg` group under the keys `state`, `hash`,
`stateBackup`, `hashBackup` and `stateWrittenAt`
([TcgStateStore.java:17-22](../src/main/java/com/osrstcg/persist/TcgStateStore.java#L17-L22)).
Every checkpoint therefore emits `ConfigChanged` events for this group. The per-key checks
are what keep those from doing anything. **Never widen this handler to react to the group
as a whole** — every save would trigger it, and any handler that writes config in response
would recurse.

## Adding a config item

A minimal checklist derived from the existing items:

1. Add the `@ConfigItem` with `keyName`, `name`, `description`, `section`, and the next free
   `position` in that section.
2. Give the `default` method a real default. There is no null handling in RuneLite's proxy
   for primitives, but reference types (`String`, `Color`, enums) *can* arrive `null` from a
   corrupted config — every enum consumer in this codebase null-coerces (e.g.
   [PullNotificationService.java:62](../src/main/java/com/osrstcg/service/PullNotificationService.java#L62)).
   Match that.
3. Inject `OsrsTcgConfig` into the consumer and read the getter at the point of use. Do not
   cache it in a field.
4. Only add a branch to `onConfigChanged` if the change must take effect before the next
   natural read — a listener registration, a scheduler cancellation, or a push into static
   state. Otherwise leave it polled.
5. If the item causes data to leave the user's machine, add a `warning` constant.

## Gotchas & invariants

- **`debugMessages` (config) is not `isDebugLogging()` (state).** Only the latter unlocks
  `::tcg-set`, `::tcg-give`, `::tcg-apex`, `::tcg-complete`, debug-only regional packs
  ([PackOpeningService.java:100](../src/main/java/com/osrstcg/service/PackOpeningService.java#L100))
  and debug booster visibility.
- **`notifyFoils` overrides `notifyTier` and `notifyNewCardsOnly`.** A `true` value means
  every foil notifies regardless of rarity or novelty
  ([PullNotificationService.java:51-64](../src/main/java/com/osrstcg/service/PullNotificationService.java#L51-L64)).
- **`dinkDuplicateNotifyTier` is dead while `dinkOnlyNotifyNew` is on.** The duplicate check
  short-circuits before the tier is ever consulted.
- **The Dink chain and the chat chain are fully independent.** Changing `notifyTier` does
  not change what Dink sends, and vice versa. There are effectively two rarity floors to
  keep in sync by hand.
- **Webhook and party broadcast are downstream of the chat filter.** Both only fire when
  `shouldNotify` returned `true` ([:97-101](../src/main/java/com/osrstcg/service/PullNotificationService.java#L97-L101)),
  so silencing chat silences them too — which is not what the section layout implies.
- **`partyAnnounceMythicPulls` is bidirectional.** It suppresses both what you send and what
  you display from others.
- **`chatPrefixColor` leaks across plugin restarts.** `PREFIX_COLOR` is static and is never
  reset in `shutDown()`.
- **The `osrstcg` config group is shared with persistence.** Group-wide config listeners will
  fire on every state checkpoint.
- **`TcgPanel` injects `OsrsTcgConfig` but reads nothing from it**
  ([TcgPanel.java:161](../src/main/java/com/osrstcg/ui/TcgPanel.java#L161),
  [:233](../src/main/java/com/osrstcg/ui/TcgPanel.java#L233)). Do not assume the sidebar
  reacts to any config item; its web-share indicator is driven by a listener callback
  registered in `startUp()`, not by config reads.

### Open questions

- The precise UI treatment RuneLite applies to `secret = true` (masked field, exclusion from
  config export/profile sync, or both) could not be confirmed from this repository, since
  `net.runelite:client` is a `compileOnly` dependency with no vendored source. Treat the key
  as sensitive regardless.
