# AltoClef in Mojang's names

`.github/workflows/remap-altoclef.yml` checks out AltoClef (MIT, https://github.com/MiranCZ/altoclef) at commit
`1f8e038`, the version for Minecraft 1.21.1, and runs Loom's `migrateMappings` (Fabric's source remapper, Mercury)
over its sources with this throwaway build, from Yarn's names to Mojang's, the names this mod is written in. The result
is committed to `third_party/altoclef/` with AltoClef's licence, as the copy the ports in `engine/` are made from and
compared against. It is not compiled.
