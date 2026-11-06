# Automatone in this mod: SecondBrainEngine's copy against sailex's

SMH plan 71, step 1.2 (ledger 1 of §5.3). The movement layer is SecondBrainEngine's Automatone (`1712b17e`), itself a
fork of sailex's Automatone (`b7953df8`, LGPL-3.0). Compared on 2026-11-06 with SecondBrainEngine's version comments
resolved to 1.21.1 (`stonecut.py`), both in Yarn's names, imports and comments set aside: 57 of 265 shared files
differ, 5 files are SecondBrainEngine's own, 10 of sailex's are not taken. Every behaviour line was read against both
sources (and Baritone 1.21.1, `11ea66c`, where it bears on it). "Covered" names the scenes that run it; "inert" means
nothing the agent does calls it, so it gets its scene when something first does.

Mechanical kinds, no behaviour of their own: **M1** players only (a living entity narrowed to a server player);
**M2** a call through a `*Ver` helper; **M3** no Cardinal Components (one Baritone per player kept in
`BaritoneProvider.playerToBaritone`); **M4** no client, rendering, chat commands or GUI; **M5** renamed, moved or
reformatted.

## Behaviour lines, with their verdicts

| File | What SecondBrainEngine changed | Verdict |
|---|---|---|
| `api/Settings.java` | `acceptableThrowawayItems`, `blocksToAvoid`, `blocksToAvoidBreaking` lists (sailex's data-pack tags inlined, the throwaway list without `#stone_crafting_materials`, the no-break list with campfires); `allowInventory` true; `minYLevelWhileMining` -64; no `logger` | kept, rebuilt from sailex's source by `remap-sbe/settings_from_source.py` (the copy was decompiled). Lists: AltoClef sets them as lists. -64: the game-test world's ground is at y -60, so every mining scene covers it |
| `behavior/InventoryBehavior.java` | tools found by item tag (`ItemTags.PICKAXES`, `stack.isIn`) instead of item class; throwaway items by list | kept: covered by `alto_pickaxe` and every mining scene |
| `utils/ToolSet.java` | blocks to avoid breaking read as a list | kept (follows the list setting) |
| `pathing/movement/MovementHelper.java` | `blocksToAvoid` read as a list; azaleas, glow lichen and cave vines not walked through | kept: azaleas are in Baritone's own list (`AzaleaBlock`), cave vines in AltoClef's avoid list, glow lichen SecondBrainEngine's own (cautious). Covered by the movement scenes |
| `cache/WorldScanner.java` | a chunk section's height counted from the world's floor (`chunk.getBottomY()`) | kept: a fix for worlds whose floor is below 0 (every 1.18+ world); covered by every scan in the game-test world |
| `behavior/PathingBehavior.java`, `process/CustomGoalProcess.java` | `ctx.world().disconnect()` removed | kept: on a server it would close the world |
| `pathing/movement/movements/MovementDescend.java` | the result's position set before the early returns instead of after (Baritone and sailex set it after) | kept, harmless: costs are unchanged and the position is only read when the cost is finite |
| `api/behavior/IPathingBehavior.java` | `isSafeToCancel()` added | kept: AltoClef asks it |
| `api/utils/RayTraceUtils.java` | a static `fluidHandling` | kept: one of AltoClef's own Baritone patches (0002) |
| `Baritone.java` | one `AltoClefSettings` per Baritone; `FishingProcess` registered; log messages to operators through this Baritone | kept: the settings object is where AltoClef's hooks will be asked (step 1.5); fishing inert; logging inert on a server without operators |
| `BaritoneProvider.java`, `api/utils/IEntityContext.java` | a player not in the map gets a fresh Baritone each time; logging through the entity's own Baritone | kept: the engine puts each body's Baritone in the map (`Agents.of`, `Agents.bodyBaritone`), so the fresh-each-time path is not taken |
| `api/utils/BlockOptionalMeta.java` | the loot-table check rewritten around an `Optional` | kept, with this mod's own patch for 1.21.1's `Optional` (`port-sbe`, the `build(null)` fix) |
| `cache/WorldData.java` | an empty cache instead of an exception | kept: no world cache on a server (M4 in spirit) |
| `process/FarmProcess.java` | pauses when it has no goals | inert (the agent does not farm through Automatone yet) |
| `utils/schematic/format/*` | MCEdit and Sponge schematic files not read | inert: the agent's builds are made in code (`BuildGoalTask`), never read from files |
| `command/defaults/DefaultCommands.java` | an alias renamed; a fish command added | inert: no chat commands are registered (`port-sbe`) |
| `utils/InputOverrideHandler.java` | on every input update the walk keys and sneak are zeroed and forward set; in water the upward speed set to 0.25 | step 1.8: off for a body its AltoClef host drives (the host applies its keys, Automatone's forced ones over its own, plan 71 §6.2 rule 2), kept for the bodies SecondBrainEngine's own AltoClef drives, which leaves the keys to Automatone (phase 2 removes it). No scene fails without either line: `automatoneSwimsUpThreeBlocks`, `automatoneDivesToABlockThreeDown` and `automatoneSneaksAsItBridgesATrench` pass without them, and the swim and dive scenes with the water rise as well |

## Files only in one of them

SecondBrainEngine's own: `settings/AltoClefSettings.java` (AltoClef's patch 0001 as a per-Baritone object),
`api/utils/InteractionController.java` and `utils/player/PlayerEntityInteractionController.java` (a server player's
game mode in place of sailex's controller), `process/FishingProcess.java` and `command/defaults/FishCommand.java`
(fishing, inert). Sailex's not taken: `AutomatoneComponents.java` (M3), `api/npc/AutomatoneNPC.java` (its own NPC
entity; the companion is a player), `api/utils/IPlayerController.java` and `utils/player/ServerPlayerController.java`
(replaced as above), `selection/SelectionRenderer.java`, `utils/GuiClick.java`, `utils/IRenderer.java`,
`utils/PathRenderer.java` and the two Schematica adapter files (M4).

## Decompiled code

`api/Settings.java` only. `mixins/MixinUtil.java` trips the provenance grep (`var3`), but it is SecondBrainEngine's
own: it copies Minecraft's own executor-shutdown routine, whose decompiled local name came with it.

## Ledger 2: our patches (SMH plan 71, §5.3)

### AltoClef's own Baritone patches, onto Automatone (step 1.5)

MiranCZ/altoclef's `research/baritone_altoclef/patches` 0002 and 0003 (LGPL-3.0, as Baritone is; kept in
`third_party/baritone_altoclef`), put onto Automatone by `.github/port-sbe/altoclef_hooks.py`, each change an exact
replacement with a comment naming its patch. AltoClef asks one static `AltoClefSettings.getInstance()`; here every place
asks its own companion's (`Baritone.getExtraBaritoneSettings()`), since Automatone runs one Baritone per body and
plans on executor threads that must read the companion they plan for. The rules ride on each `BlockStateInterface`
(every path search makes its own, for one Baritone); a place with an entity context asks its Baritone. Scenes: the 7
`alto_*` scenes and `automatoneGoesRoundAWallItsOwnerBuilt` (the owner's wall priced as never, the world's stone as
stone, the path round; without these patches the same scene broke through the wall).

| Where | What it asks | From |
|---|---|---|
| `CalculationContext` | its own rules on its `BlockStateInterface`; no breaking or placing while AltoClef holds interaction; never place where its rules say not; never break what they keep, or an end portal frame | 0002 |
| `MovementHelper.avoidBreaking` | its rules, an end portal frame, and the block under one | 0002 |
| `MovementHelper.canWalkThrough` | lava it may swim (with nothing on top); the cells its rules keep it out of, before the air check | 0002 |
| `MovementHelper.fullyPassable` (entity) | the cells its rules keep it out of | 0002 |
| `MovementHelper.canWalkOn` | an end portal frame is ground; an end portal when AltoClef says so (0002 put this in `canWalkOnBlockState`; Automatone has no state-only cache, so it goes where the rules are in reach) | 0002 |
| `MovementHelper.avoidWalkingInto` | an end portal frame | 0002 |
| `MovementHelper.canPlaceAgainst` | never place where its rules say not | 0002 |
| `MovementDescend.safeMode`, `MovementTraverse` (walking into what it breaks), `PathExecutor` (the corner cut) | the cells its rules keep it out of | 0002 |
| `MovementFall` | AltoClef's "fall, but place no bucket" | 0002 |
| `MineProcess` | waits while AltoClef holds interaction; widens the cache by the number of kinds asked for; lava and end portals count as plausible | 0002 |
| `InputOverrideHandler` | no clicks while AltoClef holds interaction | 0002 |
| `ToolSet` | a block that breaks at once is broken with what is in hand; never a tool it is saving; the tool it is told to use | 0002 |
| `InventoryBehavior` | no moves while AltoClef holds interaction; protected items are never thrown down as blocks; no block chosen for a spot its rules keep clear | 0002 |
| `BuilderProcess` | no placing and no inventory moves while AltoClef holds interaction; no placement plausible for a state that is not there | 0003 |
| `AltoClefSettings`, `RayTraceUtils` | the fluids its ray traces stop at, the companion's own (AltoClef's static, which SecondBrainEngine kept but never used: its clip passed `NONE`) | 0002 |

Not taken, each with why:

- 0002 `PathNode`: `applyGlobalHeuristic` returns its input in AltoClef itself (its loop is commented out).
- 0002 `MovementHelper`: the infested block taken off the never-break list. Kept: `SmhRules.neverBreakToPass` never
  breaks one to pass either.
- 0002 `MovementTraverse`: soul sand as an ordinary block. Automatone's traverse has no soul-sand case to switch off.
- 0002 `MineProcess`: an import and wildcard imports.
- 0002 `IBuilderProcess` and 0003: `popStack`, `isFromAltoclefFinished` and the state stack. Nothing in MiranCZ's
  AltoClef calls them.
- 0003: the break history (`getFromHistory`, `noteRemoval`, `HistoryInfo`). Never filled; asked and cleared only.
- 0003: `protectItemOfMissing` and `stopProtectItemOfMissing`, the `missing` map. The map is never filled and the
  protecting half never called.
- 0003: `isDefaultState` and `PropertyContainer`. Never called.
- 0003: `updateMovement`. It ticks the path's current movement a second time on every builder tick, a second writer of
  the keys (plan 71, I4).

Found when Automatone began asking the rules (step 1.5), and fixed in the same step:

- Our routine's own rule (`engine/agent/npc/NpcRoutineTask`: a build's standing blocks are kept) read the world
  through vanilla's `getChunkNow`, which gives null off the server thread, so it kept nothing when Automatone asked
  from a path thread and the build's blocks when the executor asked on the server thread: the path went through what
  the executor would then not break, and the agent's hut stood stuck (3 of 3 failed with the patches, 2 of 3 passed
  without). `BuildGoalTask.standingPart` reads through `WorldHelper.chunkAt` now (3 of 3).
- SecondBrainEngine's AltoClef's own "never place where an entity is" rule (`EntityStuckTracker`, not in MiranCZ's
  AltoClef) counted dropped items and experience orbs; it counts what the game's placement check counts
  (`blocksBuilding`), by `port.py`.

### Baritone's own later code, and Automatone's log (steps 1.6 and 1.7)

By `.github/port-sbe/baritone.py`, each citing Baritone `11ea66c`:

| Where | What | From |
|---|---|---|
| `api/Settings.java`, `MovementHelper.avoidBreaking` | `blocksToDisallowBreaking`, blocks it may never break; AltoClef's nether portal back on it (its `AltoClef.java:276-277`) | Baritone `Settings.java:244-249`, `MovementHelper.java:77` (step 1.6; scene `aPathPastAPortalLeavesItWhole`) |
| `utils/player/EntityContext.feetPos` | the chunk its feet are in (it asked for `(int) x << 4`), so a bottom slab underfoot puts its feet a block up | Baritone `IPlayerContext.java:63-81` (step 1.7; scene `aPathFromASlabStartsWhereItStands`) |
| `process/ExploreProcess` (its `BaritoneChunkCache`) | a chunk counts as explored once it has been within the server's view distance of where the body stood while exploring; Automatone has no chunk cache, so every chunk read as unexplored and a body at the middle of the chunk explore began in was already in its goal and stood still | Baritone `ExploreProcess.java:189-213`, `CachedWorld.java:111-118`, `GameEventHandler.java:87-110` (a client caches every chunk it is sent); after phase 1's gate, scene `aWanderFromTheMiddleOfAChunkGoesItsDistance` |
| `api/IBaritone.logDirect`, `api/utils/IEntityContext.logDebug` | Automatone's messages to the server's log as well (`ServerHooks.log`), its debug ones by this body's own `chatDebug` (it read the global one) | ours (step 1.7): it writes to its entity's chat, which a fake player shows no one; sailex's `logger` setting is client-only and stays out |

### Automatone's unit tests (step 1.9)

Its six, from sailex's `src/test` at `b7953df8`, translated into `fabric/src/test/java/com/mineagent/sbe/automatone`
(package renamed, Mojang's names, JUnit 5): cached regions, `GoalGetToBlock`, action costs, open sets (31 sizes),
pathing block types, `BetterBlockPos` (its `testSimple` carries no `@Test` upstream and never ran there; here it runs).

### Seam B (step 1.8)

By `.github/port-sbe/baritone.py` (`SEAM_B`), ours (plan 71, §6.2):

| Where | What | Why |
|---|---|---|
| `utils/InputOverrideHandler.onTickServer` | for a body whose keys its host applies (`ServerHooks.keysByHost`), the walk keys, jump, sneak and the water rise are left alone; clicks stay | rule 2, one writer per key: the host (`AltoClef.tick`) puts Automatone's forced keys over its own, as Baritone's over a client's keyboard |
| `utils/player/PlayerEntityInteractionController.onPlayerDamageBlock` | sends no STOP of its own; reports whether the game mode is still breaking | rule 3, one break finisher: the body's game mode carries a break to its end (`FakePlayerGameMode.tick`), as the old port's `BaritonePlayerController` did |

Outside this tree, in the same step: `engine/rules/BlockRules` (rule 4: one predicate for breaks and placements,
registered with each body's Automatone when its AltoClef host or its SecondBrainEngine agent is made, and asked
again by the game mode, which refuses a break it forbids and records a rule mismatch, I3; the scenes fail on any);
`engine/entity/KeyWriters` (I4: who wrote each key in each tick; the movement scenes fail on any key with two
writers). A placement is not yet asked again at the bottom: the game mode's `useItemOn` cannot tell a placement from
a use (opening a chest in a protected area would be refused), and a hook on `BlockItem.place` means a mixin per
loader; placements are asked at the planning level, where Automatone prices them.
