# SMH plan 71, step 1.2: SecondBrainEngine's api/Settings.java is decompiled (its locals are varN), so it is made here from
# sailex's Automatone source (b7953df8, src/api/java/baritone/api/Settings.java, Yarn names), with exactly the changes
# SecondBrainEngine made on purpose, read from its decompiled copy on 2026-11-06 (number formatting and version comments
# aside, every other setting matches):
#   - acceptableThrowawayItems, blocksToAvoid and blocksToAvoidBreaking are lists, as Baritone and AltoClef have them,
#     holding what sailex's data-pack tags hold (SecondBrainEngine ships no data pack): the throwaway list without the
#     #minecraft:stone_crafting_materials entry, the no-break list with campfires added, as SecondBrainEngine has them;
#   - allowInventory true (sailex false); minYLevelWhileMining -64, the 1.18+ world floor (sailex 0);
#   - no logger setting: sailex's writes to a client's chat window.
# Usage: settings_from_source.py <sailex Settings.java> <output Settings.java>, run before Loom's migrateMappings.
import re, sys

src, out = sys.argv[1], sys.argv[2]
t = open(src, encoding='utf-8').read()


def once(old, new):
    global t
    if t.count(old) != 1:
        raise SystemExit('expected once in sailex Settings.java: ' + old[:80])
    t = t.replace(old, new)


once('package baritone.api;', 'package me.sailex.automatone.api;')
t = re.sub(r'\bimport baritone\.', 'import me.sailex.automatone.', t)
once('import net.minecraft.client.MinecraftClient;\n', 'import net.minecraft.block.Blocks;\n')
once('public final Setting<Boolean> allowInventory = new Setting<>(false);', 'public final Setting<Boolean> allowInventory = new Setting<>(true);')
once('public final Setting<Integer> minYLevelWhileMining = new Setting<>(0);', 'public final Setting<Integer> minYLevelWhileMining = new Setting<>(-64);')
once('''public final Setting<TagKey<Item>> acceptableThrowawayItems = new Setting<>(TagKey.of(
            RegistryKeys.ITEM,
            Identifier.of("automatone", "throwaway_blocks")
    ));''', '''public final Setting<List<Item>> acceptableThrowawayItems = new Setting<>(new ArrayList<>(List.of(
            Blocks.DIRT.asItem(), Blocks.NETHERRACK.asItem(), Blocks.BASALT.asItem(), Blocks.STONE.asItem(),
            Blocks.BLACKSTONE.asItem(), Blocks.GRANITE.asItem(), Blocks.DIORITE.asItem(), Blocks.ANDESITE.asItem(),
            Blocks.SOUL_SOIL.asItem()
    )));''')
once('''public final Setting<TagKey<Block>> blocksToAvoid = new Setting<>(TagKey.of(RegistryKeys.BLOCK,
            Identifier.of("automatone", "avoided_blocks")
    ));''', '''public final Setting<List<Block>> blocksToAvoid = new Setting<>(new ArrayList<>(List.of()));''')
once('''public final Setting<TagKey<Block>> blocksToAvoidBreaking = new Setting<>(TagKey.of(RegistryKeys.BLOCK,
            Identifier.of("automatone", "no_break")
    ));''', '''public final Setting<List<Block>> blocksToAvoidBreaking = new Setting<>(new ArrayList<>(List.of(
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CAMPFIRE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.CHEST,
            Blocks.TRAPPED_CHEST
    )));''')
# The logger setting and its comment.
m = re.search(r'\n(\s*/\*\*(?:(?!\*/).)*?\*/\s*\n)?\s*public final Setting<Consumer<Text>> logger = new Setting<>\([^;]*\);\n', t, re.S)
if not m:
    raise SystemExit('no logger setting in sailex Settings.java')
t = t[:m.start()] + '\n' + t[m.end():]
if 'MinecraftClient' in t:
    raise SystemExit('a client reference is left in Settings.java')
open(out, 'w', encoding='utf-8').write(t)
print('Settings.java made from sailex source:', out)
