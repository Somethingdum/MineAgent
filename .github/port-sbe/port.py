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
]

# The mixins' annotation names (mixin_names.py, read as data: -I keeps this script's folder off the import path).
_names = {}
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mixin_names.py'), encoding='utf-8').read(), _names)
PATCHES += _names['MIXIN_PATCHES']

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
            text = FABRIC_IMPORTS.sub('', text)
            text = text.replace('me.sailex.', 'com.mineagent.sbe.')
            out = os.path.join(DST, rel)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            open(out, 'w', encoding='utf-8').write(header(rel) + text)
            done += 1
    left = [r for r, _, fs in os.walk(DST) for f in fs if 'net.fabricmc' in open(os.path.join(r, f), encoding='utf-8').read()]
    print('ported', done, 'files into', DST, '; files still naming net.fabricmc:', left)

main()
