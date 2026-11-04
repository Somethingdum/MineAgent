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

        Map<String, List<Source>> sources = new HashMap<>(Catalogue.withoutStorageBlocks(recipes, sources(sp, level)));
        animals(sp, level).forEach((item, list) -> sources.computeIfAbsent(item, k -> new ArrayList<>()).addAll(list));
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
        if (stack.is(ItemTags.SWORDS)) return "sword";
        if (stack.is(Items.SHEARS)) return "shears";
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
                new Probe(new ItemStack(Items.WOODEN_SWORD), "sword", 1, false),
                new Probe(new ItemStack(Items.SHEARS), "shears", 0, false));
        Map<String, List<Source>> out = new HashMap<>();
        BlockPos at = sp.blockPosition();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (block instanceof net.minecraft.world.level.block.CropBlock crop) {
                // A crop is harvested ripe and planted again (MA8.5), never broken green for its seeds.
                try {
                    BlockState ripe = crop.getStateForAge(crop.getMaxAge());
                    for (var e : drops(ripe, level, at, sp, ItemStack.EMPTY, 32).entrySet()) {
                        if (e.getValue() <= 0) continue;
                        out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Source(e.getKey(), Production.CROP + id(block), "", 0,
                                false, e.getValue(), 2));
                    }
                } catch (RuntimeException odd) {
                    // a crop a mod builds oddly: left out
                }
                continue;
            }
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
                    // Two breaks; where they differ (gravel's flint, one time in ten; a leaf's sapling), 64, so a
                    // chance is counted as the chance it is.
                    Map<String, Double> first = drops(state, level, at, sp, p.stack(), 1);
                    Map<String, Double> second = drops(state, level, at, sp, p.stack(), 1);
                    if (first.equals(second)) got.putAll(first);
                    else got.putAll(drops(state, level, at, sp, p.stack(), 64));
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

    /** What breaking a block gives with this tool, on average over {@code n} breaks. */
    private static Map<String, Double> drops(BlockState state, ServerLevel level, BlockPos at, ServerPlayer sp, ItemStack tool, int n) {
        Map<String, Double> got = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (ItemStack d : Block.getDrops(state, level, at, null, sp, tool)) got.merge(id(d.getItem()), d.getCount() / (double) n, Double::sum);
        }
        return got;
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
        // A kind with many (the catalogue, MA7.3): "a bed", "wool", "planks", "logs", "a boat": whichever of them is
        // cheapest to make here.
        for (String f : forms) {
            String suffix = "_" + f.replace(' ', '_');
            int n = 0;
            for (var key : BuiltInRegistries.ITEM.keySet()) if (key.getPath().endsWith(suffix)) n++;
            if (n >= 2) return ANY + f.replace(' ', '_');
        }
        return null;
    }

    /** An order for any one of a kind: "any:bed". */
    public static final String ANY = "any:";

    /** The items of a kind ("any:bed": every bed). */
    public static List<String> family(String any) {
        String suffix = "_" + any.substring(ANY.length());
        List<String> out = new ArrayList<>();
        for (var key : BuiltInRegistries.ITEM.keySet()) if (key.getPath().endsWith(suffix)) out.add(key.toString());
        return out;
    }

    private static final String ENTITY = Production.ENTITY;
    /** How far round it a thing lying on the ground counts as at hand. */
    public static final int GROUND = 16;
    /** Adults of a kind it leaves where it hunts, so they breed back. */
    public static final int LEAVE = 2;

    /**
     * What each animal drops (MA7.1: "mob drops from loot tables rolled in memory"): its own loot table rolled for a
     * kill by the companion, a few times, in memory; nothing spawned. Animals only: it hunts for food and materials,
     * it doesn't go looking for fights. The kill is priced by its health.
     */
    private static Map<String, List<Source>> animals(ServerPlayer sp, ServerLevel level) {
        Map<String, List<Source>> out = new HashMap<>();
        var server = level.getServer();
        // Wool by shearing, the sheep left to grow it back (the catalogue, MA7.3): 1 to 3 a sheep of that colour.
        for (var colour : net.minecraft.world.item.DyeColor.values()) {
            String wool = "minecraft:" + colour.getName() + "_wool";
            out.computeIfAbsent(wool, k -> new ArrayList<>()).add(new Source(wool, Production.SHEAR + "minecraft:sheep:" + colour.getName(),
                    "shears", 0, false, 2.0, 30));
        }
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != net.minecraft.world.entity.MobCategory.CREATURE) continue;
            try {
                var entity = type.create(level);
                if (!(entity instanceof net.minecraft.world.entity.animal.Animal living) || !huntable(living)) {
                    if (entity != null) entity.discard();
                    continue;
                }
                living.moveTo(sp.getX(), sp.getY(), sp.getZ());
                var table = server.reloadableRegistries().getLootTable(living.getLootTable()); // a sheep's by its colour: wool too
                Map<String, Double> got = new HashMap<>();
                int rolls = 4;
                for (int i = 0; i < rolls; i++) {
                    var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, living)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, sp.position())
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(sp))
                            .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY, sp)
                            .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_ATTACKING_ENTITY, sp)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER, sp)
                            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
                    for (ItemStack d : table.getRandomItems(params)) got.merge(id(d.getItem()), d.getCount() / (double) rolls, Double::sum);
                }
                living.discard();
                int ticks = 60 + (int) Math.ceil(living.getMaxHealth() / 4.0) * 12; // the walk to it, a hit every 12 ticks
                String source = ENTITY + BuiltInRegistries.ENTITY_TYPE.getKey(type);
                for (var e : got.entrySet()) {
                    if (e.getValue() <= 0) continue;
                    out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Source(e.getKey(), source, "", 0, false, e.getValue(), ticks));
                }
            } catch (RuntimeException e) {
                // A mob a mod builds oddly: left out.
            }
        }
        return out;
    }

    /**
     * An animal it may hunt for what it drops: a farm animal, grown. Never a pet or one that can be tamed, a horse or
     * the like, one that fights back (a wolf, a bee, a polar bear, a llama), or one with a name.
     */
    public static boolean huntable(net.minecraft.world.entity.Entity e) {
        return e instanceof net.minecraft.world.entity.animal.Animal a && a.isAlive()
                && !(a instanceof net.minecraft.world.entity.TamableAnimal)
                && !(a instanceof net.minecraft.world.entity.animal.horse.AbstractHorse)
                && !(a instanceof net.minecraft.world.entity.NeutralMob)
                && !a.isBaby() && !a.hasCustomName() && !a.isLeashed();
    }

    /** Foods the game has, by how much they feed (nutrition plus saturation), most first. */
    public static Map<String, Float> foods() {
        List<Map.Entry<String, Float>> all = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food == null || food.nutrition() < 4 || stack.getMaxStackSize() < 16) continue;
            String id = id(item);
            if (id.contains("rotten") || id.contains("spider_eye") || id.contains("poison") || id.contains("pufferfish")
                    || id.contains("golden") || id.contains("enchanted") || id.contains("chorus") || id.contains("suspicious")) continue;
            all.add(Map.entry(id, food.nutrition() + food.saturation()));
        }
        all.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        Map<String, Float> out = new java.util.LinkedHashMap<>();
        for (var e : all) out.put(e.getKey(), e.getValue());
        return out;
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
        return have(sp, loop, except, null, 0, 0);
    }

    /**
     * As {@link #have(ServerPlayer, com.mineagent.engine.loop.AgentLoop, BlockPos)}, for a job: what other jobs have
     * promised themselves from its chests left out, and the food and torches it keeps for itself ({@code keepFood},
     * {@code keepTorches}) not counted as there to spend (SMH plan 70, MA7.4).
     */
    public static Have have(ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop, BlockPos except, String job, int keepFood,
                            int keepTorches) {
        Have h = haveAll(sp, loop, except);
        Map<String, Integer> carried = keepFood > 0 || keepTorches > 0
                ? Reserves.afterFloors(h.carried(), foods().keySet(), keepFood, keepTorches) : h.carried();
        Map<String, Integer> stored = loop != null && job != null ? loop.itemReservations().free(h.stored(), job) : h.stored();
        return new Have(carried, stored, h.tools(), h.furnaces(), h.table(), h.blocks(), h.knownDistance(), h.smokers(), h.blastFurnaces());
    }

    private static Have haveAll(ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop, BlockPos except) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        Map<String, Integer> carried = new HashMap<>();
        List<Tool> tools = new ArrayList<>();
        var silk = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
        int furnaces = 0, smokers = 0, blasts = 0;
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
            if (s.is(Items.SMOKER)) smokers += s.getCount();
            if (s.is(Items.BLAST_FURNACE)) blasts += s.getCount();
            if (s.is(Items.CRAFTING_TABLE)) table = true;
        }
        BlockPos at = sp.blockPosition();
        // Closest source first (MA7.6): what lies on the ground near it counts as what it has; the job picks it up.
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(GROUND, 6, GROUND),
                x -> x.isAlive() && com.mineagent.engine.survival.Manners.mayTake(x, sp)
                        && !com.mineagent.engine.world.AfkFarms.protects(level.dimension().location().toString(), x.getBlockX(), x.getBlockY(), x.getBlockZ()))) {
            carried.merge(id(e.getItem().getItem()), e.getItem().getCount(), Integer::sum);
        }
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, -4, -12), at.offset(12, 4, 12))) {
            if (!level.isLoaded(p)) continue;
            var st = level.getBlockState(p);
            if (st.is(Blocks.FURNACE)) furnaces++;
            else if (st.is(Blocks.SMOKER)) smokers++;
            else if (st.is(Blocks.BLAST_FURNACE)) blasts++;
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
            if (st.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(st)) {
                String key = Production.CROP + id(st.getBlock());
                int d = (int) Math.sqrt(p.distSqr(at));
                if (!distance.containsKey(key) || d < distance.get(key)) {
                    where.put(key, Where.KNOWN);
                    distance.put(key, d);
                }
                continue;
            }
            if (!st.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            String bid = id(st.getBlock());
            int d = (int) Math.sqrt(p.distSqr(at));
            if (!distance.containsKey(bid) || d < distance.get(bid)) {
                where.put(bid, Where.KNOWN);
                distance.put(bid, d);
            }
        }
        // Animals near (more than the two of a kind it leaves), then ones it has seen, for what they drop.
        Map<String, Integer> herd = new HashMap<>();
        Map<String, Integer> herdAt = new HashMap<>();
        for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(at).inflate(48), GameKnowledge::huntable)) {
            String key = ENTITY + BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            herd.merge(key, 1, Integer::sum);
            herdAt.merge(key, (int) Math.sqrt(e.blockPosition().distSqr(at)), Math::min);
        }
        // Sheep ready to shear, by colour: shearing leaves them, so there is no two to leave.
        for (var sheep : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Sheep.class, new net.minecraft.world.phys.AABB(at).inflate(48),
                sh -> sh.isAlive() && sh.readyForShearing())) {
            String key = Production.SHEAR + "minecraft:sheep:" + sheep.getColor().getName();
            int d = (int) Math.sqrt(sheep.blockPosition().distSqr(at));
            if (!distance.containsKey(key) || d < distance.get(key)) {
                where.put(key, Where.KNOWN);
                distance.put(key, d);
            }
        }
        herd.forEach((key, n) -> {
            if (n > LEAVE) {
                where.put(key, Where.KNOWN);
                distance.put(key, herdAt.get(key));
            }
        });
        if (loop != null) {
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                if (type.getCategory() != net.minecraft.world.entity.MobCategory.CREATURE) continue;
                if (where.containsKey(ENTITY + BuiltInRegistries.ENTITY_TYPE.getKey(type)) || herd.containsKey(ENTITY + BuiltInRegistries.ENTITY_TYPE.getKey(type))) continue;
                String what = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
                var near = loop.placeKeeper().sightings().nearest(what, dim, at.getX(), at.getY(), at.getZ(), 1, 96);
                if (!near.isEmpty() && near.get(0).sighting().kind() == com.mineagent.engine.memory.Sightings.Kind.ANIMAL) {
                    String key = ENTITY + BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    where.put(key, Where.KNOWN);
                    distance.put(key, (int) near.get(0).distance());
                }
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
        return new Have(carried, stored, tools, furnaces, table, where, distance, smokers, blasts);
    }
}
