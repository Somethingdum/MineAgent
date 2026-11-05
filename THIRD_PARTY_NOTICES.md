# Third-party notices

Ideas only, no code copied: rating each step of a skill to find the one that fails, repairing that step alone,
and keeping versions with a rollback to the better one (`learning/SkillRepair.java`) follow PSN (MIT).

MineAgent is licensed under LGPL-3.0-only (see `LICENSE`). The parts below were ported from other projects
under their own licences, which are kept here as those licences require.

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

And Baritone's engine itself, from Baritone 1.21.1 (commit `11ea66c`), one for each companion's server-side body
(SMH plan 70): its path search, movements and costs, the path executor, its behaviours (pathing, looking, inventory,
the input override) and processes (custom goal, get to block, mine, follow, explore, farm, build, backfill), goals,
rotations, ray traces, schematics and settings with their defaults; with AltoClef's own fork of Baritone applied
([baritone_altoclef](https://github.com/MiranCZ/baritone_altoclef), branch `1.21.1`, LGPL-3.0: its `AltoClefSettings`
and the hooks that ask it). No world cache, commands, waypoints, elytra or rendering. Each file names the Baritone file
it came from; changes for the companion's server-side body are marked `SMH`. Into:

- `engine/src/main/java/com/mineagent/engine/baritone/` (all but `baritone/utils/SmhRules.java`, the mod's own)

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
