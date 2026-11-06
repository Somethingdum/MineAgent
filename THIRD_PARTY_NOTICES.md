# Third-party notices

Ideas only, no code copied: rating each step of a skill to find the one that fails, repairing that step alone,
and keeping versions with a rollback to the better one (`learning/SkillRepair.java`) follow PSN (MIT).

MineAgent is licensed under LGPL-3.0-only (see `LICENSE`). The parts below were ported from other projects
under their own licences, which are kept here as those licences require.

## Source pins (SMH plan 71, §7.1, answered 2026-11-05)

Every source the companion is built from, at one commit, with the checklist answered. A "no" on line 1 (source, not
decompiled), 2 (a fork's diff read) or 5 (licence) stops a pull; lines 3, 4, 6 and 7 are recorded and change how it is
handled. The decompiler check is `grep -rlE 'catch \([A-Za-z]* var[0-9]+\)' <tree> | wc -l`.

| Source | Pin | 1 Source? | 2 Upstream or fork | 3 Alive? | 4 Runs on 1.21.1 in its own project? | 5 Licence | 6 Taken / left | 7 What it assumes that a server does not give |
|---|---|---|---|---|---|---|---|---|
| AltoClef, `MiranCZ/altoclef` | `1f8e038` (2025-05-20); `third_party/altoclef` | yes: the grep finds 0 of 429 files | the upstream line: not a fork on GitHub; the original (`gaucho-matrero/altoclef`) was archived in 2024 and this continues it | dormant since 2025-05: its fixes are ours, in the port script | yes, as a client mod | MIT (Adris Jautakas) | all that runs on a server (plan 71, decision 11.15); left: `butler`, `commands`, `commandsystem`, `ui`, 28 client mixins, two example tasks | one client player (`AltoClef.getInstance()`, 337 calls in the port), a client thread reading a client world, screens for containers, client mixins for its events |
| AltoClef's Baritone patches, `MiranCZ/baritone_altoclef` | `a34f78a` (2025-04-28); `third_party/baritone_altoclef/patches` | yes: patch files | patches over Baritone's 1.21.1 line, read in full (plan 71, §3.8.1) | dormant since 2025-06 | yes, applied to Baritone, as a client mod | no licence file; they modify Baritone, LGPL-3.0, and are used under it | 0002 and 0003 (plan 71, step 1.5); left: 0001 (the settings class SecondBrainEngine already carries), 0004 (build properties) | `AltoClefSettings.getInstance()`, one static instance for one player |
| Automatone, the tree kept: SecondBrainEngine's copy, `sailex428/SecondBrainEngine` | `1712b17e` (2026-04-18); `third_party/secondbrainengine` | source except `automatone/api/Settings.java`, decompiled, rebuilt from sailex's source at plan 71 step 1.2 (`mixins/MixinUtil.java` trips the grep only by copying Minecraft's own executor-shutdown routine) | a fork of sailex's Automatone (below); its diff against it: 57 of 265 shared files differ, 5 added, 10 removed, classified at step 1.2 | dormant since 2026-04 | yes, on players, in its own project | LGPL-3.0 (its AltoClef half MIT) | its Automatone; its AltoClef half is decompiled and only a map (plan 71, §5.1), deleted at step 2.4 | static `BaritoneProvider` keyed by player (fine per body); vanilla's main-thread-only chunk lookup avoided by its own (plan 71, §3.8.2) |
| Automatone, sailex's fork, `sailex428/automatone` | `b7953df8` (2025-09-12); reference, not copied | yes | a fork of Ladysnake's Automatone (below), ported to 1.21 | dormant since 2025-09 | 1.21, any living entity | LGPL-3.0 | the source for the two decompiled files above and for each behaviour line the step 1.2 ledger reverts | Cardinal Components for one Baritone per entity; client renderers |
| Automatone, `Ladysnake/Automatone` | `545c552` (2023-11-01, 1.20.1); reference | yes | a fork of Baritone made server-side | dormant since 2023 | no (1.20.1) | LGPL-3.0 | nothing directly; the slab feet fix's upstream bug is here too (plan 71, §3.8.3) | as sailex's |
| Baritone, `cabaletta/baritone` | `11ea66c` (2026-09-01, 1.21.1); reference | yes | the upstream | yes | yes, as a client mod | LGPL-3.0 | nothing in the mod after plan 71 phase 1; each Automatone patch cites it | a client player and world |
| Mindcraft, `mindcraft-bots/mindcraft` | `e1c1fc3` (2026-10-26); `third_party/mindcraft` | yes (JavaScript) | the upstream | yes | no: a Mineflayer bot in JavaScript; translated, its scenes are its check | MIT (Kolby Nottingham) | the NPC controller, blueprints, three modes, the commands as tools, the skill lookup, the self-prompter; left: `!newAction` (the model writing code) | a bot with its own client connection; saves no routine state (plan 71, §4) |
| Voyager, `MineDojo/Voyager` | `55e45a8` (2023-07-27); `third_party/voyager` | yes (Python) | the upstream | dormant since 2023 | no: Python over Mineflayer; translated, its scenes are its check | MIT (MineDojo Team) | the curriculum, critic and skill descriptions; left: the action agent (code writing) and the embedding search (SMH rules 13, 16) | a model writing JavaScript skills; embeddings |
| Carpet, `gnembon/fabric-carpet` | `1.4.147` (`e8ea849`, its release for 1.21, the line 1.21.1 is on); reference | yes | the upstream | yes | yes | MIT (gnembon) | nothing; the check for the body's fake player | |

## SecondBrainEngine

From [SecondBrainEngine](https://github.com/sailex428/SecondBrainEngine) (sailex428, LGPL-3.0), at commit `1712b17e`:
Automatone's server-side Baritone (Ladysnake's fork of Baritone, LGPL-3.0, as kept up by sailex428) and a server-side
adaptation of AltoClef (MIT, Copyright (c) 2020 Adris Jautakas), one of each for a server player: the one agent's
movement and doing layers. Its sources were read as text, resolved to 1.21.1 and remapped to Mojang's names
(`.github/remap-sbe`, kept in `third_party/secondbrainengine` with its licence), and ported by
`.github/port-sbe/port.py` (its package renamed, the companion's changes marked SMH) into:

- `engine/src/main/java/com/mineagent/sbe/` (the mod's own `ServerHooks.java` in it is written for this mod)

Its licence is the GNU Lesser General Public License, version 3, the same licence as this mod (see `LICENSE`); its
AltoClef parts keep AltoClef's MIT notice, below.

## altoclef

From [altoclef](https://github.com/MiranCZ/altoclef) (MIT), at commit `1f8e038`:
the arrow closest-approach and block test (`ProjectileHelper`, `MobDefenseChain`), the
"strike at full charge, shield up between hits" rule (`KillAura`, its SMART strategy), the
"raise the shield for a skeleton that has drawn for 15 ticks" rule, and the lists of mobs
too dangerous to fight without gear. Ported, in plain coordinates and with one correction
(a rising arrow's height), into:

- `engine/src/main/java/com/mineagent/engine/combat/ArrowPredictor.java`
- `engine/src/main/java/com/mineagent/engine/combat/CombatBrain.java`
- `engine/src/main/java/com/mineagent/engine/combat/MobPolicy.java`
- `engine/src/main/java/com/mineagent/engine/survival/MobDefenseChain.java`

And the deciding of its food task (`CollectFoodTask`: food on the ground first, then ripe crops, then the animal in
sight scored by the food it gives over the distance squared, sweet berries last, and when none is in sight a wander
to new ground and a look again, as `TimeoutWanderTask` does), rewritten for the companion's own body, into:

- `engine/src/main/java/com/mineagent/engine/task/CollectFoodTask.java`

And AltoClef itself, ported whole for the companion's server-side body (SMH plan 70): its task system, trackers,
helpers, slot model, settings, item catalogue (`TaskCatalogue`) and its tasks (resources, crafting, smelting, mining,
movement, construction, containers, entities, the speedrun tasks), 1f8e038 in Mojang's names
(`third_party/altoclef`, made by Loom's `migrateMappings`), with only the changes a body without a client needs, each
marked `SMH` in the file, which also names the AltoClef file it came from, into:

- `engine/src/main/java/com/mineagent/engine/alto/` (all but the mod's own `alto/AltoTreeTask.java`, `alto/AltoLifeTask.java`,
  `alto/control/ServerGameMode.java`, `alto/tasks/life/` (its first days, from AltoClef's priority tasks, and the model's
  wants) and `alto/tasks/construction/BuildHutTask.java` (below, under Mindcraft); `PrioritySelector` is lifted out of
  `BeatMinecraftTask`), its survival chains (food, mob defence, the MLG bucket, getting unstuck, equipping ahead, lava,
  fire and drowning) and the tasks they use among them

`third_party/altoclef/` keeps the copy they are made from, with AltoClef's `LICENSE`.

```
MIT License

Copyright (c) 2020 Adris Jautakas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Baritone

From [Baritone](https://github.com/cabaletta/baritone) (LGPL-3.0, as this mod), branch `1.20.4`: the move straight
down through the block under its feet, its rule and its cost (`MovementDownward`: ground two under, the block's mining
time plus a fall of one), and the shortest partial path worth walking (`AbstractNodeCostSearch.MIN_DIST_PATH`, five
blocks). Rewritten for the mod's own pathing, into:

- `engine/src/main/java/com/mineagent/engine/pathing/moves/movements/MovementDownward.java`
- `engine/src/main/java/com/mineagent/engine/pathing/astar/AStarPathFinder.java` (`MIN_DIST_PATH`)

Its engine (1.21.1, commit `11ea66c`, with AltoClef's own patches) was ported by hand into `engine/baritone/` for the
AltoClef port until 2026-11-05, when SMH plan 71 (step 1.1) put that port on Automatone instead (SecondBrainEngine, above)
and deleted the copy; it stays in this repository's history at `afb11cb` as the map for AltoClef's patches (step 1.5).

AltoClef's own patches to Baritone (`MiranCZ/baritone_altoclef` at `a34f78a`, patches 0002 and 0003, modifying
Baritone and used under its LGPL-3.0; kept in `third_party/baritone_altoclef`) were put onto Automatone at step 1.5 by
`.github/port-sbe/altoclef_hooks.py`, each place asking its own companion's settings; what was taken and what was not
is in `.github/port-sbe/AUTOMATONE-LEDGER.md` (ledger 2). Two pieces of Baritone's own later code were put onto
Automatone the same way (`.github/port-sbe/baritone.py`, plan 71 steps 1.6 and 1.7): `blocksToDisallowBreaking`
(`Settings.java:244-249`, `MovementHelper.java:77`) and the feet position on a slab (`IPlayerContext.java:63-81`).

Automatone's six unit tests (`src/test` of `sailex428/automatone` at `b7953df8`, LGPL-3.0, from Ladysnake's
Automatone and Baritone) were translated into `fabric/src/test/java/com/mineagent/sbe/automatone` (plan 71, step
1.9), each keeping its licence header.

Baritone is licensed under the GNU Lesser General Public License, version 3, the same licence as this mod (see
`LICENSE`).

## Mindcraft

From [Mindcraft](https://github.com/kolbytn/mindcraft) (MIT): its `dirt_shelter` blueprint
(`src/agent/npc/construction/dirt_shelter.json`), read as its `BuildGoal` reads a blueprint (layers bottom up, each row
north to south, its first layer `offset` below the place, an empty name for any block), for the companion's hut, built by
Baritone's builder. Into:

- `engine/src/main/java/com/mineagent/engine/alto/tasks/construction/BuildHutTask.java`

Its `giveToPlayer` skill (`src/agent/library/skills.js`, at `mindcraft-bots/mindcraft` `e1c1fc3`): to within 3 blocks of
the player and the items tossed there, or from where its walk got no closer, the player in sight. Into:

- `engine/src/main/java/com/mineagent/engine/task/GiveTask.java`

Its NPC controller (`src/agent/npc/`, at `e1c1fc3`): the construction blueprints (`construction/*.json`, copied to
`engine/src/main/resources/mineagent/blueprints/`) as `controller.js` reads them, and `utils.js` with the lists from
`src/utils/mcdata.js`. Into:

- `engine/src/main/java/com/mineagent/engine/agent/npc/`

```
MIT License

Copyright (c) 2024 Kolby Nottingham

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Voyager

From [Voyager](https://github.com/MineDojo/Voyager) (MIT), at commit `55e45a8`, kept unchanged in `third_party/voyager`
with its licence: the reference its learning loop is translated from (SMH plan 71, phase 4).

```
MIT License

Copyright (c) 2023 MineDojo Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
