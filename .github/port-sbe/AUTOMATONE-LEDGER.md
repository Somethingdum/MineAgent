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
| `utils/InputOverrideHandler.java` | on every input update the walk keys and sneak are zeroed and forward set; in water the upward speed set to 0.25 | **open, step 1.8**: kept only if the swim, dive and sneak scenes fail without them; neither sailex nor Ladysnake has them |

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
