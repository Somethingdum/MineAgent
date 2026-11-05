# Third-party notices

Ideas only, no code copied: rating each step of a skill to find the one that fails, repairing that step alone,
and keeping versions with a rollback to the better one (`learning/SkillRepair.java`) follow PSN (MIT).

MineAgent is licensed under LGPL-3.0-only (see `LICENSE`). The parts below were ported from other projects
under their own licences, which are kept here as those licences require.

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

- `engine/src/main/java/com/mineagent/engine/alto/` (all but `alto/AltoTreeTask.java`, `alto/control/ServerGameMode.java`
  and `alto/baritone/`)

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

And the parts of Baritone AltoClef's tasks call, from Baritone 1.21.1 (commit `11ea66c`): its goals, rotations, ray
traces, inputs, schematics, the checks of what not to break and what a block can be placed against, the tool speed rule
and the settings with their defaults; with `AltoClefSettings` and AltoClef's changes to them from AltoClef's own fork
of Baritone ([baritone_altoclef](https://github.com/MiranCZ/baritone_altoclef), branch `1.21.1`, LGPL-3.0). Each file
names the Baritone file it came from; changes for the companion's server-side body are marked `SMH`. Into:

- `engine/src/main/java/com/mineagent/engine/alto/baritone/`

Baritone is licensed under the GNU Lesser General Public License, version 3, the same licence as this mod (see
`LICENSE`).
