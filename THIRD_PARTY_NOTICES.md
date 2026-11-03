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
