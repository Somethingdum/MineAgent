# SMH plan 70, one agent: SecondBrainEngine's movement and doing layers (third_party/secondbrainengine, 1.21.1 in Mojang's
# names) ported into the mod as com.mineagent.sbe. Re-runnable: the package renamed, a source line put on each file, and
# every change for the companion made here, as an exact replacement asserted to match once ("SMH" marks each in the code).
# Usage (from the repo root): python3 -I .github/port-sbe/port.py
import os, re, shutil, sys

SRC = 'third_party/secondbrainengine/src/main/java/me/sailex'
DST = 'engine/src/main/java/com/mineagent/sbe'
COMMIT = '1712b17e'
SKIP = {
    # Carpet drives its fake players by "/player <name> move" commands; the companion's body has its own input driver
    # (ServerHooks.commandHelper, set by the engine), so this one Carpet class is not taken.
    'automatone/utils/CarpetPlayerCommandHelper.java',
}

def header(rel):
    return ('// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,\n'
            '// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at ' + COMMIT + ':\n'
            '// src/main/java/me/sailex/' + rel + ', as 1.21.1 in Mojang\'s names (third_party/secondbrainengine).\n'
            '// Changes for the companion are marked SMH (.github/port-sbe/port.py).\n')

HOOKS = 'com.mineagent.sbe.common.ServerHooks'

# (file, old, new): each old must occur exactly once in the renamed file.
PATCHES = [
    # DestroyBlockTask given back AltoClef's own way out of a block it can't get at (gaucho-matrero/altoclef
    # DestroyBlockTask: a MovementProgressChecker; when it fails, the block requested unreachable and a short wander):
    # SecondBrainEngine's left it all to Automatone's builder, which with no face in sight and no path waited for good
    # (a world-3 run spent half a day under one spruce log behind leaves).
    ('altoclef/tasks/construction/DestroyBlockTask.java',
     '''   private boolean isClear;
''',
     '''   private boolean isClear;
   // SMH: AltoClef's own (see port.py).
   private final com.mineagent.sbe.altoclef.util.progresscheck.MovementProgressChecker moveChecker =
      new com.mineagent.sbe.altoclef.util.progresscheck.MovementProgressChecker();
   private final com.mineagent.sbe.altoclef.tasks.movement.TimeoutWanderTask wanderTask =
      new com.mineagent.sbe.altoclef.tasks.movement.TimeoutWanderTask(5.0F);
'''),
    ('altoclef/tasks/construction/DestroyBlockTask.java',
     '''      this.isClear = false;
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      builder.clearArea(this.pos, this.pos);''',
     '''      this.isClear = false;
      this.moveChecker.reset(); // SMH
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      builder.clearArea(this.pos, this.pos);'''),
    ('altoclef/tasks/construction/DestroyBlockTask.java',
     '''   protected Task onTick() {
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();''',
     '''   protected Task onTick() {
      // SMH: AltoClef's own: no progress, so the block is marked unreachable and it wanders a little; the task asking
      // for it picks another.
      if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
         this.setDebugState("Can't get at it: wandering.");
         this.moveChecker.reset();
         return this.wanderTask;
      }
      if (!this.moveChecker.check(this.controller)) {
         this.moveChecker.reset();
         this.controller.getBlockScanner().requestBlockUnreachable(this.pos);
         this.controller.getBaritone().getBuilderProcess().onLostControl();
         this.setDebugState("Can't get at it: marked unreachable.");
         return this.wanderTask;
      }
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();'''),
    # A crafting table a walk dearer than making one is passed over, as AltoClef's own DoStuffInContainerTask decides
    # with CraftInTableTask.getCostToMakeNew (none within 40 blocks, and logs or 4 planks in hand: 10; else 100):
    # SecondBrainEngine went to the nearest known table at any distance, and a world-1 run spent three days making for
    # one 91 blocks under it (in a trial chamber) with no way there.
    ('altoclef/tasks/container/CraftInTableTask.java',
     '''            Optional<BlockPos> nearestTable = controller.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE);''',
     '''            // SMH: AltoClef's own rule (DoStuffInContainerTask, CraftInTableTask.getCostToMakeNew).
            net.minecraft.world.phys.Vec3 here = EntityVer.getPos(controller.getEntity());
            double makeNew = controller.getItemStorage().hasItem(com.mineagent.sbe.altoclef.util.helpers.ItemHelper.LOG)
                    || controller.getItemStorage().getItemCount(com.mineagent.sbe.altoclef.util.helpers.ItemHelper.PLANKS) >= 4 ? 10 : 100;
            Optional<BlockPos> nearestTable = controller.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE)
                    .filter(t -> t.closerToCenterThan(here, 40)
                            || com.mineagent.sbe.altoclef.util.helpers.BaritoneHelper.calculateGenericHeuristic(here,
                                    net.minecraft.world.phys.Vec3.atCenterOf(t)) <= makeNew);'''),
    # Each wood's and each colour's entry named prefix + "_" + baseName, as AltoClef's own TaskCatalogue names them
    # (gaucho-matrero/altoclef, colorfulTasks and woodTasks); SecondBrainEngine's copy has prefix + "_" + prefix, so
    # "spruce_door", "white_bed", "oak_fence" and every other such item were not in the catalogue (the agent's hut,
    # short of only its door, gave up on it: "Every goal met").
    ('altoclef/TaskCatalogue.java',
     '''         put(prefix + "_" + prefix, new Item[]{getMatch.apply(color)}, count -> getTask.apply(color, count));''',
     '''         put(prefix + "_" + baseName, new Item[]{getMatch.apply(color)}, count -> getTask.apply(color, count)); // SMH: AltoClef's own'''),
    ('altoclef/TaskCatalogue.java',
     '''      return woodTasks(woodItem -> woodItem.prefix + "_" + woodItem.prefix, getMatch, getTask, requireNetherForNetherStuff);''',
     '''      return woodTasks(woodItem -> woodItem.prefix + "_" + baseName, getMatch, getTask, requireNetherForNetherStuff); // SMH: AltoClef's own'''),
    # Cook up to what it has cooked already and the raw on top (AltoClef's own getBestSmeltTarget: toSmelt = raw +
    # cooked); SecondBrainEngine asked for only as many cooked as it had raw, so with that many cooked in hand the
    # smoking was done before it began and CollectFoodTask handed it back every tick (a world-3 run sat on it 16,000).
    ('altoclef/tasks/resources/CollectFoodTask.java',
     '''            return new SmeltTarget(new ItemTarget(cookable.getCooked(), rawCount), new ItemTarget(cookable.getRaw(), rawCount));''',
     '''            int toSmelt = rawCount + controller.getItemStorage().getItemCount(cookable.getCooked()); // SMH: AltoClef's own
            return new SmeltTarget(new ItemTarget(cookable.getCooked(), toSmelt), new ItemTarget(cookable.getRaw(), rawCount));'''),
    # A tracker refreshed on the server thread only; asked from Baritone's path workers it answers from its last state
    # (at most a tick old: TrackerManager marks every tracker dirty each tick). A refresh reads the world, and on a
    # server a read from another thread waits for the server thread, which can be waiting on the lock the refresh holds
    # (BaritoneHelper.MINECRAFT_LOCK): the benchmark's seed 3 hung so at tick 18,884. On the client AltoClef was written
    # for, such reads do not wait, so it never met this.
    ('altoclef/trackers/Tracker.java',
     '''   public void ensureUpdated() {
      if (this.isDirty()) {''',
     '''   public void ensureUpdated() {
      // SMH: on the server thread only (see .github/port-sbe/port.py); elsewhere its last state.
      if (this.isDirty() && (this.mod == null || this.mod.getWorld() == null || this.mod.getWorld().getServer().isSameThread())) {'''),
    # AltoClef's game timers on the game's time again, ticks / 20 as AltoClef's own TimerGame counts it (the client
    # world's time); SecondBrainEngine counted the wall clock, so a server faster than real time (a sprint, the game
    # tests) waited thousands of ticks on a 2-second craft, and one slower hurried.
    ('altoclef/util/time/TimerGame.java',
     '''         return (double) System.currentTimeMillis() / 1000;''',
     '''         return (double) ''' + HOOKS + '''.ticks() / 20.0; // SMH: the game's time, as AltoClef's own'''),
    # 1.21.1's LootContext.Builder.create takes an Optional (its random sequence), not a nullable name: null threw on the
    # first block's drops, so every mine, clear and build stopped (upstream's own line, SecondBrainEngine at 1712b17e).
    ('automatone/api/utils/BlockOptionalMeta.java',
     '''                            .create(null),''',
     '''                            .create(java.util.Optional.empty()), // SMH: 1.21.1 takes an Optional'''),
    # The companion's own tick drives it (the engine ticks each controller), not a Fabric server-tick listener.
    ('common/ServerTickable.java',
     '''    default void registerTickListener() {
        ServerTickEvents.END_SERVER_TICK.register((server -> {
            if (isEnabled()) {
                onTick();
            }
        }));
    }''',
     '''    default void registerTickListener() {
        // SMH: ticked by the companion's own tick (the engine calls onTick), not a loader's server-tick listener.
    }'''),
    ('common/ServerTickable.java', 'import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;\n', ''),

    # Chunks loaded, from the loader's own chunk events through ServerHooks; "is it loaded" asked without loading it.
    ('altoclef/trackers/SimpleChunkTracker.java',
     '''      ServerChunkEvents.CHUNK_LOAD.register((Load)(evt, chunk) -> this.onLoad(chunk.getPos()));
      ServerChunkEvents.CHUNK_UNLOAD.register((Unload)(evt, chunk) -> this.onUnload(chunk.getPos()));''',
     '''      // SMH: the loader's chunk events through ServerHooks; and the chunks already loaded round it when it starts.
      ''' + HOOKS + '''.onChunkLoad((level, pos) -> this.onLoad(pos));
      ''' + HOOKS + '''.onChunkUnload((level, pos) -> this.onUnload(pos));
      this.loaded.addAll(''' + HOOKS + '''.loadedChunksAround(mod.getPlayer()));'''),
    ('altoclef/trackers/SimpleChunkTracker.java',
     '''      return !(this.mod.getWorld().getChunk(pos.x, pos.z) instanceof EmptyLevelChunk);''',
     '''      // SMH: a server's getChunk loads (or makes) the chunk asked for; a client's gives an empty one. Asked without loading.
      return this.mod.getWorld().getChunkSource().getChunkNow(pos.x, pos.z) != null;'''),
    ('altoclef/tasks/movement/ChunkSearchTask.java',
     '''      ServerChunkEvents.CHUNK_LOAD.register((Load)(evt, chunk) -> {
         if (chunk != null) {
            synchronized (this.searchMutex) {
               if (!this.searchedAlready.contains(chunk.getPos())) {
                  this.justLoaded.add(chunk.getPos());
               }
            }
         }
      });''',
     '''      // SMH: through ServerHooks, and let go when it stops (a loader's listener is never removed).
      if (this.chunkSubscription != null) this.chunkSubscription.run();
      this.chunkSubscription = ''' + HOOKS + '''.onChunkLoad((level, pos) -> {
         synchronized (this.searchMutex) {
            if (!this.searchedAlready.contains(pos)) {
               this.justLoaded.add(pos);
            }
         }
      });'''),
    ('altoclef/tasks/movement/ChunkSearchTask.java',
     '''   protected void onStop(Task interruptTask) {
   }''',
     '''   protected void onStop(Task interruptTask) {
      if (this.chunkSubscription != null) this.chunkSubscription.run(); // SMH
      this.chunkSubscription = null;
   }

   private Runnable chunkSubscription; // SMH'''),
    ('altoclef/tasks/movement/SearchChunksExploreTask.java',
     '''      ServerChunkEvents.CHUNK_LOAD.register((Load)(evt, chunk) -> this.onChunkLoad(chunk.getPos()));''',
     '''      // SMH: through ServerHooks, and let go when it stops (a loader's listener is never removed).
      if (this.chunkSubscription != null) this.chunkSubscription.run();
      this.chunkSubscription = ''' + HOOKS + '''.onChunkLoad((level, pos) -> this.onChunkLoad(pos));'''),
    ('altoclef/tasks/movement/SearchChunksExploreTask.java',
     '''   protected void onStop(Task interruptTask) {
   }''',
     '''   protected void onStop(Task interruptTask) {
      if (this.chunkSubscription != null) this.chunkSubscription.run(); // SMH
      this.chunkSubscription = null;
   }

   private Runnable chunkSubscription; // SMH'''),

    # The game's own directories, without a loader's API.
    ('automatone/utils/SettingsLoader.java',
     'FabricLoader.getInstance().getConfigDir().resolve("automatone")',
     HOOKS + '.configDir().resolve("automatone") /* SMH */'),
    ('automatone/api/process/IBuilderProcess.java',
     'FabricLoader.getInstance().getGameDir()', HOOKS + '.gameDir() /* SMH */'),
    ('automatone/api/command/datatypes/RelativeFile.java',
     'FabricLoader.getInstance().getGameDir()', HOOKS + '.gameDir() /* SMH */'),
    ('automatone/command/defaults/BuildCommand.java',
     'FabricLoader.getInstance().getGameDir()', HOOKS + '.gameDir() /* SMH */'),
    ('automatone/command/defaults/ExploreFilterCommand.java',
     'FabricLoader.getInstance().getGameDir()', HOOKS + '.gameDir() /* SMH */'),
    ('automatone/command/defaults/VersionCommand.java',
     '''        Version version = FabricLoader.getInstance().getModContainer(Automatone.MOD_ID)
                .map(ModContainer::getMetadata)
                .map(ModMetadata::getVersion)
                .orElseThrow(() -> new CommandInvalidStateException("Null version (this may be normal in a dev environment)"));
        logDirect(source, String.format("You are running Automatone v%s", version.getFriendlyString()));''',
     '''        // SMH: part of the mod, not a mod of its own: the version it was ported from.
        logDirect(source, "You are running Automatone as ported from SecondBrainEngine ''' + COMMIT + '''");'''),

    # The companion's Baritone takes no chat commands from players; its commands are run in code.
    ('automatone/command/defaults/DefaultCommands.java',
     '''        CommandRegistrationCallback.EVENT.register(((dispatcher, ctx, dedicated) -> register(dispatcher)));''',
     '''        // SMH: no chat commands registered with the server (the companion's Baritone is driven in code).'''),

    # A plain registry of schematic formats, not one registered with the game's root registry.
    ('automatone/utils/schematic/SchematicSystem.java',
     '''FabricRegistryBuilder.createSimple(ISchematicFormat.class, ResourceLocation.fromNamespaceAndPath("automatone", "schematics")).buildAndRegister();''',
     '''new net.minecraft.core.MappedRegistry<>(net.minecraft.resources.ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("automatone", "schematics")), com.mojang.serialization.Lifecycle.stable()); // SMH: not in the game's root registry'''),

    # Not a mod entrypoint: set up by the engine.
    ('automatone/Automatone.java', 'public final class Automatone implements ModInitializer {',
     'public final class Automatone { // SMH: not a mod of its own; the engine calls onInitialize once'),
    ('automatone/Automatone.java', '''    @Override
    public void onInitialize() {''', '''    public static void onInitialize() {'''),

    # The companion's body is moved by its own input driver, not Carpet's "/player" commands.
    ('automatone/Baritone.java', 'private final CarpetPlayerCommandHelper commandHelper;',
     'private final me.sailex.automatone.api.utils.ICommandHelper commandHelper; // SMH: the companion\'s own input'),
    ('automatone/Baritone.java', 'this.commandHelper = new CarpetPlayerCommandHelper(this, player);',
     'this.commandHelper = ' + HOOKS + '.commandHelper(this, player); // SMH'),
    ('automatone/Baritone.java', 'import me.sailex.automatone.utils.CarpetPlayerCommandHelper;\n', ''),

    # AltoClef's settings files under the game's config directory, not in the server's working directory.
    ('altoclef/util/helpers/ConfigHelper.java',
     '      String fullPath = "me/sailex/altoclef" + File.separator + path;\n      return new File(fullPath);',
     '      // SMH: under the game\'s config directory (it was a folder "me/sailex/altoclef" wherever the server was started).\n'
     '      return ' + HOOKS + '.configDir().resolve("mineagent").resolve("altoclef").resolve(path).toFile();'),

    # SMH plan 71, step 1.5: SecondBrainEngine's own "never place where an entity is" rule (not in MiranCZ's AltoClef)
    # counted every entity in reach, dropped items and experience orbs too, so once Automatone asks it (AltoClef's patch
    # 0002, step 1.5) a dropped item would keep a block from going where the game would let it. It counts what the
    # game's own placement check counts (EntityGetter.isUnobstructed: blocksBuilding).
    ('altoclef/trackers/EntityStuckTracker.java',
     'if (entity != null && entity.isAlive() && !entity.equals(clientPlayerEntity) && clientPlayerEntity.closerThan(entity, 25.0)) {',
     'if (entity != null && entity.isAlive() && entity.blocksBuilding // SMH: what keeps a block from being placed\n'
     '               && !entity.equals(clientPlayerEntity) && clientPlayerEntity.closerThan(entity, 25.0)) {'),

    # SMH plan 71, I1: the block scanner's scan thread read each chunk by Level.getChunk, which on a server loads or
    # generates it, and off the server thread waits on the server thread to do so (the guard counted 680 in one scene).
    # It reads the loaded chunk through Automatone's view of the visible chunk map, as Automatone's own search does, and
    # skips one that is not loaded, as a client's scanner sees nothing there.
    ('altoclef/commands/BlockScanner.java',
     '      LevelChunk chunk = this.mod.getWorld().getChunk(chunkPos.x, chunkPos.z);\n',
     '      // SMH: never loading it, and safe on the scan thread (plan 71, I1).\n'
     '      LevelChunk chunk = ((me.sailex.automatone.utils.accessor.ServerChunkManagerAccessor) world.getChunkSource())\n'
     '         .automatone$getChunkNow(chunkPos.x, chunkPos.z);\n'
     '      if (chunk == null) {\n'
     '         return;\n'
     '      }\n'),
]

# The mixins' annotation names (mixin_names.py, read as data: -I keeps this script's folder off the import path).
_names = {}
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mixin_names.py'), encoding='utf-8').read(), _names)
PATCHES += _names['MIXIN_PATCHES']
# AltoClef's own Baritone patches, onto Automatone, each place asking its own companion's rules (altoclef_hooks.py).
_hooks = {}
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'altoclef_hooks.py'), encoding='utf-8').read(), _hooks)
PATCHES += _hooks['HOOKS']
# Jackson left out (jackson.py, read the same way).
_jackson = {}
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'jackson.py'), encoding='utf-8').read(), _jackson)
SKIP |= _jackson['SKIP']

FABRIC_IMPORTS = re.compile(r'^import net\.fabricmc\.[^;]*;\n', re.M)

def main():
    # The files it made before go (each starts with its source line); the mod's own files in the tree stay.
    for r, _, fs in os.walk(DST) if os.path.exists(DST) else []:
        for f in fs:
            p = os.path.join(r, f)
            with open(p, encoding='utf-8') as fh:
                if fh.readline().startswith('// Ported from SecondBrainEngine'):
                    os.remove(p)
    done = 0
    for root, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith('.java'):
                continue
            path = os.path.join(root, f)
            rel = os.path.relpath(path, SRC).replace(os.sep, '/')
            if rel in SKIP:
                continue
            text = open(path, encoding='utf-8').read()
            for (pf, old, new) in PATCHES:
                if pf == rel:
                    n = text.count(old)
                    if n != 1:
                        sys.exit('patch for %s matched %d times: %r' % (rel, n, old[:80]))
                    text = text.replace(old, new)
            text = _jackson['strip'](rel, text)
            text = FABRIC_IMPORTS.sub('', text)
            text = text.replace('me.sailex.', 'com.mineagent.sbe.')
            out = os.path.join(DST, rel)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            open(out, 'w', encoding='utf-8').write(header(rel) + text)
            done += 1
    left = [r for r, _, fs in os.walk(DST) for f in fs if 'net.fabricmc' in open(os.path.join(r, f), encoding='utf-8').read()]
    print('ported', done, 'files into', DST, '; files still naming net.fabricmc:', left)

main()
