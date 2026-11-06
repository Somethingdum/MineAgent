# SecondBrainEngine in Mojang's names

`third_party/secondbrainengine` is [SecondBrainEngine](https://github.com/sailex428/SecondBrainEngine) (sailex428,
LGPL-3.0) at commit `1712b17e`: Automatone's server-side Baritone and a server-side AltoClef, already run together on
server fake players. It is where the mod's movement and doing layers are ported from (SMH plan 70, one agent).

How it was made (`.github/workflows/remap-secondbrainengine.yml`), reading its sources as text only:

1. `stonecut.py 1.21.1` resolves its Stonecutter version comments for 1.21.1 (the branch whose condition holds is kept,
   uncommented; the others and the markers are dropped).
2. Its `automatone/api/Settings.java`, the one decompiled file of its Automatone, is made instead from sailex's Automatone
   source (`b7953df8`) by `settings_from_source.py`, with the changes SecondBrainEngine made on purpose (the script
   lists them). SMH plan 71, step 1.2.
3. Loom's `migrateMappings` (this throwaway Gradle project, with its 1.21.1 dependencies) translates them from Yarn's
   names to Mojang's.

Its licence is in `LICENSE` next to the sources; the mod's notices are in `THIRD_PARTY_NOTICES.md`.
