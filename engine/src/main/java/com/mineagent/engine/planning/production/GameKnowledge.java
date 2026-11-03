package com.mineagent.engine.planning.production;

import com.mineagent.engine.planning.production.Production.Book;
import com.mineagent.engine.planning.production.Production.Have;
import com.mineagent.engine.planning.production.Production.Kind;
import com.mineagent.engine.planning.production.Production.Need;
import com.mineagent.engine.planning.production.Production.Recipe;
import com.mineagent.engine.planning.production.Production.Source;
import com.mineagent.engine.planning.production.Production.Tool;
import com.mineagent.engine.planning.production.Production.Where;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the game knows, read from the running game (SMH plan 70, block 3: "what the game knows, in code"; the owner:
 * "Ensure that it has good crafting knowledge"): every recipe the server has (crafting, smelting, blasting, smoking,
 * campfire, stonecutting), what every block drops with each tool (by the block's own loot table: stone gives
 * cobblestone, or stone with Silk Touch; coal ore needs a pickaxe), how long it takes to break, each fuel's burn time,
 * each tool's uses. Nothing is written in by hand, so a data pack's or a mod's recipes count too. Read once a server.
 */
public final class GameKnowledge {
    private static volatile Book cached;
    private static volatile Object cachedFor;

    private GameKnowledge() {}

    /** The book for this server, made the first time it is asked for (about a second, on the server thread). */
    public static Book book(ServerPlayer sp) {
        Object server = sp.getServer();
        Book b = cached;
        if (b != null && cachedFor == server) return b;
        synchronized (GameKnowledge.class) {
            if (cached == null || cachedFor != server) {
                cached = read(sp);
                cachedFor = server;
            }
            return cached;
        }
    }

    static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static Book read(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        var access = level.registryAccess();
        Map<String, List<Recipe>> recipes = new HashMap<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            try {
                var r = holder.value();
                if (r.isSpecial()) continue;
                ItemStack out = r.getResultItem(access);
                if (out.isEmpty()) continue;
                Kind kind;
                int cook = 0;
                boolean table = false;
                if (r.getType() == RecipeType.CRAFTING && r instanceof CraftingRecipe c) {
                    kind = Kind.CRAFT;
                    table = !c.canCraftInDimensions(2, 2);
                } else if (r instanceof AbstractCookingRecipe cooking) {
                    kind = r.getType() == RecipeType.SMELTING ? Kind.SMELT : r.getType() == RecipeType.BLASTING ? Kind.BLAST
                            : r.getType() == RecipeType.SMOKING ? Kind.SMOKE : Kind.CAMPFIRE;
                    cook = cooking.getCookingTime();
                } else if (r.getType() == RecipeType.STONECUTTING) {
                    kind = Kind.STONECUT;
                } else {
                    continue;
                }
                List<Need> needs = needs(r.getIngredients());
                if (needs.isEmpty()) continue;
                String output = id(out.getItem());
                recipes.computeIfAbsent(output, k -> new ArrayList<>())
                        .add(new Recipe(holder.id().toString(), kind, output, out.getCount(), needs, table, cook));
            } catch (RuntimeException odd) {
                // A recipe a mod builds oddly: left out.
            }
        }

        Map<String, List<Source>> sources = sources(sp, level);
        Map<String, Integer> fuel = new HashMap<>();
        for (var e : AbstractFurnaceBlockEntity.getFuel().entrySet()) fuel.put(id(e.getKey()), e.getValue());
        Map<String, Integer> durability = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.getMaxDamage() > 0 && kindOf(stack) != null) durability.put(id(item), stack.getMaxDamage());
        }
        return new Book(recipes, sources, fuel, durability);
    }

    /** A recipe's ingredients as counts of any one of a set of items: the same ingredient twice is one need of 2. */
    static List<Need> needs(List<Ingredient> ingredients) {
        Map<List<String>, Integer> counted = new LinkedHashMap<>();
        for (Ingredient ing : ingredients) {
            if (ing == null || ing.isEmpty()) continue;
            List<String> any = new ArrayList<>();
            for (ItemStack s : ing.getItems()) {
                String i = id(s.getItem());
                if (!any.contains(i)) any.add(i);
            }
            if (any.isEmpty()) continue;
            counted.merge(any, 1, Integer::sum);
        }
        List<Need> out = new ArrayList<>();
        counted.forEach((any, n) -> out.add(new Need(List.copyOf(any), n)));
        return out;
    }

    /** "pickaxe", "axe", "shovel", "hoe" for a tool, else null. */
    static String kindOf(ItemStack stack) {
        if (stack.is(ItemTags.PICKAXES)) return "pickaxe";
        if (stack.is(ItemTags.AXES)) return "axe";
        if (stack.is(ItemTags.SHOVELS)) return "shovel";
        if (stack.is(ItemTags.HOES)) return "hoe";
        return null;
    }

    /**
     * What each block drops with each tool, by its loot table: by hand, with each pickaxe from wood up, with Silk
     * Touch, with a shovel, an axe and shears. Each item keeps the least it takes to get it.
     */
    private static Map<String, List<Source>> sources(ServerPlayer sp, ServerLevel level) {
        var silk = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPick.enchant(silk, 1);
        record Probe(ItemStack stack, String kind, int tier, boolean silk) {}
        List<Probe> probes = List.of(
                new Probe(ItemStack.EMPTY, "", 0, false),
                new Probe(new ItemStack(Items.WOODEN_PICKAXE), "pickaxe", 1, false),
                new Probe(new ItemStack(Items.STONE_PICKAXE), "pickaxe", 2, false),
                new Probe(new ItemStack(Items.IRON_PICKAXE), "pickaxe", 3, false),
                new Probe(new ItemStack(Items.DIAMOND_PICKAXE), "pickaxe", 4, false),
                new Probe(silkPick, "pickaxe", 4, true),
                new Probe(new ItemStack(Items.WOODEN_SHOVEL), "shovel", 1, false),
                new Probe(new ItemStack(Items.WOODEN_AXE), "axe", 1, false),
                new Probe(new ItemStack(Items.SHEARS), "shears", 0, false));
        Map<String, List<Source>> out = new HashMap<>();
        BlockPos at = sp.blockPosition();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            float hardness;
            try {
                hardness = state.getDestroySpeed(level, at);
            } catch (RuntimeException e) {
                continue;
            }
            if (hardness < 0) continue;
            Map<String, Source> best = new HashMap<>();
            for (Probe p : probes) {
                // The game asks whether the tool harvests the block when it breaks, not in the loot table: stone by
                // hand drops nothing, though its table says cobblestone.
                boolean correct = !state.requiresCorrectToolForDrops() || (!p.stack().isEmpty() && p.stack().isCorrectToolForDrops(state));
                if (!correct) continue;
                Map<String, Double> got = new HashMap<>();
                try {
                    for (int sample = 0; sample < 2; sample++) {
                        for (ItemStack d : Block.getDrops(state, level, at, null, sp, p.stack())) {
                            got.merge(id(d.getItem()), d.getCount() / 2.0, Double::sum);
                        }
                    }
                } catch (RuntimeException e) {
                    continue;
                }
                if (got.isEmpty()) continue;
                float speed = p.stack().isEmpty() ? 1.0f : Math.max(1.0f, p.stack().getDestroySpeed(state));
                int ticks = hardness == 0 ? 1 : (int) Math.ceil(hardness * 30.0 / speed);
                for (var e : got.entrySet()) {
                    String item = e.getKey();
                    String key = item + (p.silk() ? "|silk" : "");
                    Source old = best.get(key);
                    // Least it takes: by hand before a tool, a lower tier before a higher one.
                    if (old == null) {
                        best.put(key, new Source(item, id(block), p.kind(), p.tier(), p.silk(), e.getValue(), ticks));
                    }
                }
            }
            // Silk Touch only where it gives what nothing else does (stone, not more cobblestone).
            for (var e : best.entrySet()) {
                Source s = e.getValue();
                if (s.silkTouch() && best.containsKey(s.item())) continue;
                out.computeIfAbsent(s.item(), k -> new ArrayList<>()).add(s);
            }
        }
        return out;
    }

    /**
     * The item a person means ("stone bricks", "torches", "an iron pickaxe"): its id in their words, singular or
     * plural, else its name in the game; null if none.
     */
    public static String itemFor(String words) {
        if (words == null) return null;
        String w = words.toLowerCase(java.util.Locale.ROOT).strip().replaceFirst("^(?:the|some|of|more) ", "").replaceAll("\\s+", " ");
        if (w.isEmpty()) return null;
        java.util.List<String> forms = new java.util.ArrayList<>(java.util.List.of(w));
        if (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes")) forms.add(w.substring(0, w.length() - 2));
        if (w.endsWith("s")) forms.add(w.substring(0, w.length() - 1));
        if (w.endsWith("ies")) forms.add(w.substring(0, w.length() - 3) + "y");
        forms.add(w + "s");
        for (String f : forms) {
            var id = net.minecraft.resources.ResourceLocation.tryParse("minecraft:" + f.replace(' ', '_'));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR) return id.toString();
        }
        for (Item item : BuiltInRegistries.ITEM) {
            String name = item.getDescription().getString().toLowerCase(java.util.Locale.ROOT);
            if (forms.contains(name) && item != Items.AIR) return id(item);
        }
        return null;
    }

    /** Blocks next to it everywhere: never a search to find (stone underground, earth, sand, trees). */
    static final Set<String> COMMON = Set.of("minecraft:stone", "minecraft:deepslate", "minecraft:dirt", "minecraft:grass_block",
            "minecraft:sand", "minecraft:gravel", "minecraft:netherrack", "minecraft:cobblestone", "minecraft:andesite",
            "minecraft:diorite", "minecraft:granite", "minecraft:tuff");

    /**
     * What the companion has and knows, for the planner: what it carries, what is in the chests it knows within 64
     * blocks (but {@code except}, the chest being filled), its tools, furnaces and a table at hand (placed within 12
     * blocks, or carried), and where the blocks are: the common ones, ore it has seen, trees if it is in the overworld.
     */
    public static Have have(ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop, BlockPos except) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        Map<String, Integer> carried = new HashMap<>();
        List<Tool> tools = new ArrayList<>();
        var silk = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
        int furnaces = 0;
        boolean table = false;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            carried.merge(id(s.getItem()), s.getCount(), Integer::sum);
            String kind = kindOf(s);
            if (kind != null && s.isDamageableItem()) {
                tools.add(new Tool(kind, ProductionPlanner.tierOf(id(s.getItem())),
                        EnchantmentHelper.getItemEnchantmentLevel(silk, s) > 0, Math.max(0, s.getMaxDamage() - s.getDamageValue() - 1)));
            }
            if (s.is(Items.FURNACE)) furnaces += s.getCount();
            if (s.is(Items.CRAFTING_TABLE)) table = true;
        }
        BlockPos at = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, -4, -12), at.offset(12, 4, 12))) {
            if (!level.isLoaded(p)) continue;
            var st = level.getBlockState(p);
            if (st.is(Blocks.FURNACE)) furnaces++;
            else if (st.is(Blocks.CRAFTING_TABLE)) table = true;
        }
        Map<String, Integer> stored = new HashMap<>();
        if (loop != null) {
            for (var c : loop.chestMemory().near(dim, at.getX(), at.getY(), at.getZ(), 64.0)) {
                if (except != null && c.x() == except.getX() && c.y() == except.getY() && c.z() == except.getZ()) continue;
                c.items().forEach((item, n) -> stored.merge(item.contains(":") ? item : "minecraft:" + item, n, Integer::sum));
            }
        }
        Map<String, Where> where = new HashMap<>();
        for (String b : COMMON) where.put(b, Where.COMMON);
        Map<String, Integer> distance = new HashMap<>();
        // Trees it can see from here: logs are where trees are, not everywhere (a desert, a cave, a test field).
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-24, -6, -24), at.offset(24, 12, 24))) {
            if (!level.isLoaded(p)) continue;
            var st = level.getBlockState(p);
            if (!st.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            String bid = id(st.getBlock());
            int d = (int) Math.sqrt(p.distSqr(at));
            if (!distance.containsKey(bid) || d < distance.get(bid)) {
                where.put(bid, Where.KNOWN);
                distance.put(bid, d);
            }
        }
        if (loop != null) {
            var seen = loop.placeKeeper().sightings();
            for (Block b : BuiltInRegistries.BLOCK) {
                String bid = id(b);
                if (!bid.endsWith("_ore") && !bid.equals("minecraft:ancient_debris")) continue;
                String what = com.mineagent.engine.memory.Sightings.base(bid);
                var near = seen.nearest(what, dim, at.getX(), at.getY(), at.getZ(), 1, 256);
                if (!near.isEmpty()) {
                    where.put(bid, Where.KNOWN);
                    distance.put(bid, (int) near.get(0).distance());
                }
            }
        }
        return new Have(carried, stored, tools, furnaces, table, where, distance);
    }
}
