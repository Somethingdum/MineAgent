package com.mineagent.api.config;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public record MineAgentConfig(
    LLMConfig llm,
    CompanionConfig companion,
    SurvivalConfig survival,
    PathfindingConfig pathfinding,
    BehaviourConfig behaviour,
    MemoryConfig memory
) {
    /** The four sections a file from before the behaviour switches has; the switches take their defaults. */
    public MineAgentConfig(LLMConfig llm, CompanionConfig companion, SurvivalConfig survival,
                           PathfindingConfig pathfinding) {
        this(llm, companion, survival, pathfinding, BehaviourConfig.DEFAULTS, MemoryConfig.DEFAULTS);
    }

    public MineAgentConfig(LLMConfig llm, CompanionConfig companion, SurvivalConfig survival,
                           PathfindingConfig pathfinding, BehaviourConfig behaviour) {
        this(llm, companion, survival, pathfinding, behaviour, MemoryConfig.DEFAULTS);
    }

    /**
     * How much it remembers (SMH plan 70, MA5.7).
     *
     * @param thingsSeen  sightings kept (ore, animals, mobs, notable blocks, structures, dangers), 1,000 to 500,000
     * @param journalDays days the journal keeps in full; a summary of each day is kept for good (1 to 3,650)
     * @param chests      chests and what is in them, 64 to 16,384
     */
    public record MemoryConfig(int thingsSeen, int journalDays, int chests) {
        public static final MemoryConfig DEFAULTS = new MemoryConfig(50_000, 90, 4_096);
    }

    public record LLMConfig(
        String provider,
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        int maxTokens
    ) {
        /**
         * SMH on this PC (SMH plan 69; the user, 2026-11-03: "Just drag and drop to the folder. And start SMH."):
         * smh answers the game on this PC at this address with no key, and picks the model for each call's role.
         */
        public static final LLMConfig DEFAULTS = new LLMConfig(
            "openai-compatible", "", "smh/auto", "http://127.0.0.1:47821/apps", 0.7, 4096
        );

        /** The upstream default this fork shipped before SMH: untouched, it reached no model (it has no key). */
        public static boolean untouchedOldDefault(String provider, String apiKey, String model, String baseUrl) {
            return "deepseek".equals(provider) && (apiKey == null || apiKey.isBlank())
                    && "deepseek-chat".equals(model) && (baseUrl == null || baseUrl.isBlank());
        }
    }

    public record CompanionConfig(
        String name,
        String gameMode,
        String skinName,
        boolean instantBreak,
        boolean creativeReach
    ) {
        public static final CompanionConfig DEFAULTS = new CompanionConfig(
            "MineAgent", "survival", "", false, false
        );
    }

    public record SurvivalConfig(
        int foodCritical,
        int foodLow,
        double healthFlee,
        int stuckTimeTicks,
        boolean autoEat,
        boolean fightBack,
        boolean pickupItems,
        boolean avoidCreeper
    ) {
        public static final SurvivalConfig DEFAULTS = new SurvivalConfig(
            6, 12, 6.0, 60, true, true, true, true
        );
    }

    public record PathfindingConfig(
        int maxSearchNodes,
        boolean allowDigThrough,
        boolean allowBridge,
        boolean allowParkour
    ) {
        public static final PathfindingConfig DEFAULTS = new PathfindingConfig(
            5000, true, true, true
        );
    }

    /**
     * A switch for each of SMH plan 70's block 1 behaviours (its "Switches" table): checks that only stop mistakes, and
     * what the owner asked for, on; what the owner left optional, off. AFK only means its owner is away: what it
     * does and how far it goes stay its own (the owner, Nov 3: "AFK should be optional in terms of jobs and distance.
     * AFK just means I'm away, likely stay busy"), unless they turn on guarding or a distance.
     *
     * @param neverBreakBuilt  never break blocks someone built (or placed) to get somewhere; doors are used instead
     * @param rememberPlaced   remember the blocks real players place
     * @param closeDoors       close the doors and gates it opened behind it
     * @param afkGuardMe       guard its owner while they're AFK out in the open, instead of its work (off; named
     *                         afkGuard before, on: a file saved then keeps no say over it)
     * @param afkMinutes       no input for this long counts as AFK (1 to 60)
     * @param sleepWithPlayers go to bed when a player in its world does, so the night can pass
     * @param routeMemory      keep its trail, the places it knows and the routes between them
     * @param sprint           sprint on long straight walks (by the game's sprint rules)
     * @param saveWithWorld    save companions whenever the game saves its players
     * @param swim             swim across water at the surface instead of only walking round it
     * @param waterDrops       drop into water from any height (water of any depth breaks a fall)
     * @param bucketDescents   drop further onto ground with a water bucket, only from heights it would survive if the
     *                         water failed
     * @param avoidMobs        paths keep clear of hostile mobs, creepers most of all
     * @param longTrips        plan long trips over the chunks it has seen first
     * @param afkStayWithin    while its owner is AFK, keep within this many blocks of them (0, the default: no limit;
     *                         up to 256)
     * @param afkFarmLeftAlone at a farm its owner is AFK at, place and break nothing within 16 blocks of them and
     *                         leave the farm's mobs be
     * @param afkKeepBusy      while its owner is AFK and it has nothing to do, it finds its own work, as in auto mode
     * @param huntForOrders    hunt animals for what an order needs ("fill a chest with food"): grown farm animals only,
     *                         never pets, horses, ones with a name or the last two of a kind near it
     * @param oreSightOnly     mining on its own, it takes only ore it has seen (opened by its tunnels), as a player
     *                         does; off, also ore within 2 blocks of them
     * @param readyCheck       before a trip away from base (mining), it gets what it should carry first: food,
     *                         torches, a spare pickaxe; and it keeps that much for itself, never spent on an order
     * @param readyFood        food it carries for a trip (0 to 64)
     * @param readyTorches     torches it carries for a trip (0 to 64)
     * @param sparePickaxe     a spare pickaxe for a trip
     * @param escapeLavaFire   out of lava the shortest safe way; water on itself when burning
     * @param sleepWhenAway    its owner away, it sleeps every night it can; with no bed, a shelter until morning, and
     *                         a bed made as soon as it can
     * @param liveOnOwn        its owner away and nothing asked of it, it keeps itself fed, armed and stocked with
     *                         torches, by code, from what it carries or sees (no model call needed)
     * @param primeMeat        "prime the world": cooked meat each (0 to 128)
     * @param primeTorches     "prime the world": torches each (0 to 128)
     * @param primeFullIron    "prime the world": a full iron set each (armour, sword, pickaxe); off, a helmet each
     * @param manners          never takes what a player threw unless handed it or asked; never shoots past a player
     * @param recoverDrops     when its owner dies, it fetches their things from where they died
     * @param feedOwner        hands its owner food when they run low (keeping its own floor)
     * @param offerTools       tells its owner when their tool is nearly broken and it has or can make another
     * @param partner          takes the other half of its owner's job when it has nothing else to do
     * @param guardPerimeter   while guarding: lights the perimeter and shoots creepers before they reach it
     * @param fightPlayers     fights players its owner names, and players who attack it or its owner first
     * @param huntDrops        after hunting a named player, picks up what they dropped for its owner
     * @param liveTracking     a hunt may use the server's live position of the player it hunts (off: where it last
     *                         saw them)
     * @param replantTrees     the foot of a tree it fells gets a sapling of its kind, when it carries one
     * @param putOutFires fire on blocks near it (not a fireplace on netherrack, nor in the Nether) put out
     * @param pickBerries ripe sweet berries and glow berries picked for food, the bush left to grow more
     * @param lavaFuel a big smelting job may burn lava from a pool near it, a bucket at a time
     * @param keepPhrases the phrases it learned, kept with the world across restarts
     * @param talkSummary a short running summary of talks in each world, given to the model with its brief
     * @param fillHoles the holes it dug in the ground near home filled when it has nothing else to do
     * @param huntMonsters hostile mobs in sight hunted when an order needs what they drop (blaze rods, ender pearls, string)
     * @param gearGoal on Hard, or where local difficulty is high, it makes the iron armour it lacks when it has nothing else to do
     * @param bowAtRange with a bow and arrows it shoots what is coming, archers and creepers from range, aimed as the arrow flies
     * @param kitGoal with nothing else to do it makes a shield and golden apples to keep on it
     * @param readChestLabels signs and item frames on chests say what goes in them, as its owner's sorting system has it
     * @param fillCraters the holes creepers blow in the ground near home are filled with what the ground there is mostly made of
     * @param tradesInPlans the villagers near are traded with when that is the cheapest way to what it is making (a fletcher's arrows, emeralds for sticks)
     * @param branchForOre an ore it needs and finds none of near is got by a branch mine at its best height
     * @param golemIron a village's own iron golems are a way to iron, fought from a pillar three blocks high; never one a player built
     * @param weighFights a mob is fought only when there is a reason and it can afford it, or the danger is close and can't be got away from; its owner only when they could be seriously hurt
     * @param tradingMastery near a village with nothing to do it rolls a librarian for Mending, buys one and walls the villager in with its lectern (asking first)
     * @param wholeTrees a tree it cuts into is cut down whole (no floating tops), its leaves broken for saplings if it has none, and a sapling planted where it will grow
     * @param hoard with nothing to do and a home, it fills a chest of its own there toward its stockpile (food, torches, iron, golden carrots and apples, diamonds...), never from your chests
     * @param godGear in free time it climbs to god-tier gear (diamond, then the best books in the best anvil order); off with "speedrun"
     * @param dumpJunk in free time its junk (rock and dirt past what it keeps, useless mob drops) is thrown into lava or deep water
     * @param grindXp in free time at night, once its armour, weapons and stock are doing well, it hunts mobs for the levels enchanting and anvils need (SMH plan 70)
     * @param watchChests it notices who takes from its own chests and makes them give it back (SMH plan 70)
     * @param revengeThieves whoever takes from its chests is killed the next time it sees them, and what they drop taken back (off; or say kill thieves on sight)
     * @param autoRespawn five seconds after it dies it comes back at its bed or the world spawn, as a player does (never in Hardcore; three quick deaths and it waits for /mineagent respawn)
     * @param afkImprove while its owner is AFK, with their orders done, it works on itself as it does on its own (its kit, gear, stockpile, food store, levels) till they're back
     * @param takeTrapTnt a temple trap's TNT, once its plate is gone, broken by hand and picked up
     * @param nightStopsWork at night, out under the sky without its iron gear, with its owner away or living on its own, it stops what it is doing and digs in till morning; the work goes on after
     * @param wayOutOfHoles when its job's own rules find no way and it is below the ground where it stands (a pit, a shaft it mined, a cave) it looks again digging and pillaring as a player would (never what someone built)
     * @param dugoutHomes its own home hut, with no blocks for walls on it, is dug into a hillside or down a stairway first (no materials needed); a hut is built only when that can't be done here
     * @param homeAtNight on its own or its owner away, it goes home before dusk, closes its hut's ways in and sleeps there or waits till the undead have burned
     * @param reactiveFood its food got by deciding again and again from what is there now (food on the ground, ripe crops, animals in sight, berries), new ground looked over when none, not planned ahead from memory
     * @param altoTasks an order for an item kept on it is done by AltoClef's own task for that item, from its catalogue (mined, crafted, smelted, hunted), decided again every tick from what is there; off, by the production planner
     * @param altoLife living on its own, its first days (stone tools, a hut with its crafting table and furnace, fuel, food, a bed if it can, else a night waited out in its hut) are run by AltoClef's priority tasks, chosen again every tick from what is there; off, by its starter kit orders
     * @param altoSurvival while one of AltoClef's tasks has its body, AltoClef's own survival chains (mob defence, the MLG bucket, getting unstuck, lava, fire and drowning, eating) look after it, and the companion's chains for those stand down; off, the companion's own chains do
     * @param oneAgent living on its own it is run by the one agent (SecondBrainEngine's AltoClef on Automatone's Baritone, with Mindcraft's daily routine over its goals); off, by the earlier ways
     */
    public record BehaviourConfig(
        boolean neverBreakBuilt,
        boolean rememberPlaced,
        boolean closeDoors,
        boolean afkGuardMe,
        int afkMinutes,
        boolean sleepWithPlayers,
        boolean routeMemory,
        boolean sprint,
        boolean saveWithWorld,
        boolean swim,
        boolean waterDrops,
        boolean bucketDescents,
        boolean avoidMobs,
        boolean longTrips,
        int afkStayWithin,
        boolean afkFarmLeftAlone,
        boolean afkKeepBusy,
        boolean huntForOrders,
        boolean oreSightOnly,
        boolean readyCheck,
        int readyFood,
        int readyTorches,
        boolean sparePickaxe,
        boolean escapeLavaFire,
        boolean sleepWhenAway,
        boolean liveOnOwn,
        int primeMeat,
        int primeTorches,
        boolean primeFullIron,
        boolean manners,
        boolean recoverDrops,
        boolean feedOwner,
        boolean offerTools,
        boolean partner,
        boolean guardPerimeter,
        boolean fightPlayers,
        boolean huntDrops,
        boolean liveTracking,
        boolean replantTrees,
        boolean putOutFires,
        boolean pickBerries,
        boolean lavaFuel,
        boolean keepPhrases,
        boolean talkSummary,
        boolean fillHoles,
        boolean huntMonsters,
        boolean gearGoal,
        boolean bowAtRange,
        boolean kitGoal,
        boolean readChestLabels,
        boolean fillCraters,
        boolean tradesInPlans,
        boolean branchForOre,
        boolean golemIron,
        boolean weighFights,
        boolean tradingMastery,
        boolean wholeTrees,
        boolean hoard,
        boolean godGear,
        boolean dumpJunk,
        boolean grindXp,
        boolean watchChests,
        boolean revengeThieves,
        boolean autoRespawn,
        boolean afkImprove,
        boolean takeTrapTnt,
        boolean nightStopsWork,
        boolean wayOutOfHoles,
        boolean dugoutHomes,
        boolean homeAtNight,
        boolean reactiveFood,
        boolean altoTasks,
        boolean altoLife,
        boolean altoSurvival,
        boolean oneAgent
    ) {
        public static final BehaviourConfig DEFAULTS = new BehaviourConfig(
            true, true, true, false, 3, true, true, true, true, true, true, true, true, true, 0, true, true, true, true,
            true, 8, 16, true, true, true, true, 64, 32, true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true, true, true, false, false, false, false
        );
    }

    public static final MineAgentConfig DEFAULTS = new MineAgentConfig(
        LLMConfig.DEFAULTS, CompanionConfig.DEFAULTS,
        SurvivalConfig.DEFAULTS, PathfindingConfig.DEFAULTS, BehaviourConfig.DEFAULTS, MemoryConfig.DEFAULTS
    );

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting().serializeNulls().create();

    public static MineAgentConfig load(Path configDir) {
        Path configFile = configDir.resolve("mineagent.json");
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    throw new JsonParseException("configuration root must be an object");
                }
                // Overlay old/partial files onto current defaults. Gson maps a
                // missing record to null and missing primitive booleans to
                // false, which made ordinary version upgrades crash or disable
                // newly introduced safety features.
                JsonObject merged = GSON.toJsonTree(DEFAULTS).getAsJsonObject();
                mergeInto(merged, parsed.getAsJsonObject());
                return normalize(GSON.fromJson(merged, MineAgentConfig.class));
            } catch (Exception e) {
                System.err.println("[MineAgent] Failed to load config: " + e.getMessage());
                System.err.println("[MineAgent] Using defaults");
            }
        }
        // Save defaults
        saveDefaults(configFile);
        return DEFAULTS;
    }

    private static void mergeInto(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement existing = target.get(entry.getKey());
            JsonElement incoming = entry.getValue();
            if (existing != null && existing.isJsonObject()
                    && incoming != null && incoming.isJsonObject()) {
                mergeInto(existing.getAsJsonObject(), incoming.getAsJsonObject());
            } else if (target.has(entry.getKey()) && incoming != null && !incoming.isJsonNull()) {
                target.add(entry.getKey(), incoming.deepCopy());
            }
        }
    }

    private static MineAgentConfig normalize(MineAgentConfig loaded) {
        if (loaded == null) return DEFAULTS;
        LLMConfig rawLlm = loaded.llm != null ? loaded.llm : LLMConfig.DEFAULTS;
        double temperature = Double.isFinite(rawLlm.temperature)
                && rawLlm.temperature >= 0.0 && rawLlm.temperature <= 2.0
                ? rawLlm.temperature : LLMConfig.DEFAULTS.temperature;
        int maxTokens = rawLlm.maxTokens > 0 && rawLlm.maxTokens <= 1_000_000
                ? rawLlm.maxTokens : LLMConfig.DEFAULTS.maxTokens;
        LLMConfig llm = new LLMConfig(
                nonBlank(rawLlm.provider, LLMConfig.DEFAULTS.provider),
                nonNull(rawLlm.apiKey),
                nonBlank(rawLlm.model, LLMConfig.DEFAULTS.model),
                nonNull(rawLlm.baseUrl), temperature, maxTokens);
        // A file written by an older jar with the old, keyless default: SMH on this PC instead (it never worked).
        if (LLMConfig.untouchedOldDefault(llm.provider, llm.apiKey, llm.model, llm.baseUrl)) {
            llm = new LLMConfig(LLMConfig.DEFAULTS.provider, "", LLMConfig.DEFAULTS.model,
                    LLMConfig.DEFAULTS.baseUrl, temperature, maxTokens);
        }

        CompanionConfig rawCompanion = loaded.companion != null
                ? loaded.companion : CompanionConfig.DEFAULTS;
        CompanionConfig companion = new CompanionConfig(
                nonBlank(rawCompanion.name, CompanionConfig.DEFAULTS.name),
                com.mineagent.api.entity.CompanionGameMode.orDefault(
                        rawCompanion.gameMode).wireName(),
                nonNull(rawCompanion.skinName), rawCompanion.instantBreak,
                rawCompanion.creativeReach);

        SurvivalConfig rawSurvival = loaded.survival != null
                ? loaded.survival : SurvivalConfig.DEFAULTS;
        int foodCritical = clamp(rawSurvival.foodCritical, 0, 20);
        int foodLow = clamp(rawSurvival.foodLow, foodCritical, 20);
        double healthFlee = Double.isFinite(rawSurvival.healthFlee)
                && rawSurvival.healthFlee >= 0.0
                ? rawSurvival.healthFlee : SurvivalConfig.DEFAULTS.healthFlee;
        SurvivalConfig survival = new SurvivalConfig(foodCritical, foodLow,
                healthFlee, Math.max(1, rawSurvival.stuckTimeTicks),
                rawSurvival.autoEat, rawSurvival.fightBack,
                rawSurvival.pickupItems, rawSurvival.avoidCreeper);

        PathfindingConfig rawPathfinding = loaded.pathfinding != null
                ? loaded.pathfinding : PathfindingConfig.DEFAULTS;
        PathfindingConfig pathfinding = new PathfindingConfig(
                clamp(rawPathfinding.maxSearchNodes, 128, 1_000_000),
                rawPathfinding.allowDigThrough, rawPathfinding.allowBridge,
                rawPathfinding.allowParkour);
        BehaviourConfig rawBehaviour = loaded.behaviour != null ? loaded.behaviour : BehaviourConfig.DEFAULTS;
        BehaviourConfig behaviour = new BehaviourConfig(rawBehaviour.neverBreakBuilt, rawBehaviour.rememberPlaced,
                rawBehaviour.closeDoors, rawBehaviour.afkGuardMe, clamp(rawBehaviour.afkMinutes, 1, 60),
                rawBehaviour.sleepWithPlayers, rawBehaviour.routeMemory, rawBehaviour.sprint,
                rawBehaviour.saveWithWorld, rawBehaviour.swim, rawBehaviour.waterDrops, rawBehaviour.bucketDescents,
                rawBehaviour.avoidMobs, rawBehaviour.longTrips, clamp(rawBehaviour.afkStayWithin, 0, 256),
                rawBehaviour.afkFarmLeftAlone, rawBehaviour.afkKeepBusy, rawBehaviour.huntForOrders, rawBehaviour.oreSightOnly,
                rawBehaviour.readyCheck, clamp(rawBehaviour.readyFood, 0, 64), clamp(rawBehaviour.readyTorches, 0, 64),
                rawBehaviour.sparePickaxe, rawBehaviour.escapeLavaFire, rawBehaviour.sleepWhenAway, rawBehaviour.liveOnOwn,
                clamp(rawBehaviour.primeMeat, 0, 128), clamp(rawBehaviour.primeTorches, 0, 128), rawBehaviour.primeFullIron,
                rawBehaviour.manners, rawBehaviour.recoverDrops, rawBehaviour.feedOwner, rawBehaviour.offerTools,
                rawBehaviour.partner, rawBehaviour.guardPerimeter, rawBehaviour.fightPlayers, rawBehaviour.huntDrops,
                rawBehaviour.liveTracking, rawBehaviour.replantTrees, rawBehaviour.putOutFires, rawBehaviour.pickBerries, rawBehaviour.lavaFuel, rawBehaviour.keepPhrases, rawBehaviour.talkSummary, rawBehaviour.fillHoles, rawBehaviour.huntMonsters, rawBehaviour.gearGoal, rawBehaviour.bowAtRange, rawBehaviour.kitGoal, rawBehaviour.readChestLabels, rawBehaviour.fillCraters, rawBehaviour.tradesInPlans, rawBehaviour.branchForOre, rawBehaviour.golemIron, rawBehaviour.weighFights, rawBehaviour.tradingMastery, rawBehaviour.wholeTrees, rawBehaviour.hoard, rawBehaviour.godGear, rawBehaviour.dumpJunk, rawBehaviour.grindXp, rawBehaviour.watchChests, rawBehaviour.revengeThieves, rawBehaviour.autoRespawn, rawBehaviour.afkImprove, rawBehaviour.takeTrapTnt, rawBehaviour.nightStopsWork, rawBehaviour.wayOutOfHoles, rawBehaviour.dugoutHomes, rawBehaviour.homeAtNight, rawBehaviour.reactiveFood, rawBehaviour.altoTasks, rawBehaviour.altoLife, rawBehaviour.altoSurvival, rawBehaviour.oneAgent);
        MemoryConfig rawMemory = loaded.memory != null ? loaded.memory : MemoryConfig.DEFAULTS;
        MemoryConfig memory = new MemoryConfig(clamp(rawMemory.thingsSeen, 1_000, 500_000),
                clamp(rawMemory.journalDays, 1, 3_650), clamp(rawMemory.chests, 64, 16_384));
        return new MineAgentConfig(llm, companion, survival, pathfinding, behaviour, memory);
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    private static String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void saveDefaults(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(DEFAULTS, writer);
            }
            System.out.println("[MineAgent] Created default config at " + configFile);
        } catch (Exception e) {
            System.err.println("[MineAgent] Failed to save default config: " + e.getMessage());
        }
    }

    /**
     * Save this configuration to the {@code mineagent.json} file in the given
     * config directory. Used by the {@code /mineagent setconfig} command to
     * persist in-game config changes.
     *
     * @param configDir the platform config directory
     */
    public boolean save(Path configDir) {
        Path configFile = configDir.resolve("mineagent.json");
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            System.out.println("[MineAgent] Config saved to " + configFile);
            return true;
        } catch (Exception e) {
            System.err.println("[MineAgent] Failed to save config: " + e.getMessage());
            return false;
        }
    }
}
