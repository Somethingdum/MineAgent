# SMH plan 71, §5.3, ledger 2: our patches to Automatone that take Baritone's own later code (cabaletta/baritone,
# LGPL-3.0, 1.21.1 line at 11ea66c; read from its source), each citing the lines it follows. Automatone forked an
# older Baritone. Loaded by port.py after AltoClef's hooks (altoclef_hooks.py); each old text is found once.

BARITONE = [
    # Step 1.6: blocks it may never break (Baritone's Settings.java:244-249 and MovementHelper.java:77). Automatone has
    # only blocksToAvoidBreaking, which makes breaking slower (ToolSet), not forbidden; AltoClef puts the nether portal
    # on both (its AltoClef.java:276-277).
    ('automatone/api/Settings.java',
     '''    public final Setting<List<Block>> blocksToAvoidBreaking = new Setting<>(new ArrayList<>(List.of(
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CAMPFIRE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.CHEST,
            Blocks.TRAPPED_CHEST
    )));
''',
     '''    public final Setting<List<Block>> blocksToAvoidBreaking = new Setting<>(new ArrayList<>(List.of(
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CAMPFIRE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.CHEST,
            Blocks.TRAPPED_CHEST
    )));

    /**
     * Blocks that Baritone is not allowed to break
     */
    // SMH: Baritone's own (Settings.java:244-249 at 11ea66c); Automatone forked a Baritone without it.
    public final Setting<List<Block>> blocksToDisallowBreaking = new Setting<>(new ArrayList<>(
            // Leave Empty by Default
    ));
'''),
    ('automatone/pathing/movement/MovementHelper.java',
     '''        return b instanceof net.minecraft.world.level.block.EndPortalFrameBlock
                || b == Blocks.ICE''',
     '''        return settings.blocksToDisallowBreaking.get().contains(b) // SMH: Baritone's MovementHelper.java:77 at 11ea66c
                || b instanceof net.minecraft.world.level.block.EndPortalFrameBlock
                || b == Blocks.ICE'''),

    # Step 1.7: its feet on a slab (Baritone's IPlayerContext.playerFeet, IPlayerContext.java:63-81 at 11ea66c: a
    # bottom slab where its feet are means it stands one block up). Automatone asked for chunk (int) x << 4, sixteen
    # times as far as the chunk the feet are in, so the check read no chunk and a path from a slab began a block low.
    # The chunk is the feet's own; it is read through Automatone's view, which loads nothing.
    ('automatone/utils/player/EntityContext.java',
     '((ServerChunkManagerAccessor) world.getChunkSource()).automatone$getChunkNow((int) x << 4, (int) z << 4);',
     '((ServerChunkManagerAccessor) world.getChunkSource()).automatone$getChunkNow(feet.getX() >> 4, feet.getZ() >> 4); // SMH'),
]

# Step 1.7: Automatone's log, routed to ours (ServerHooks.log): it writes to its entity's chat, which a fake player
# shows no one. Not Baritone's code; ours, kept here with the other patches of ledger 2.
LOG = [
    ('automatone/api/IBaritone.java',
     '''    default void logDirect(Component... components) {
        IEntityContext playerContext = this.getPlayerContext();
        LivingEntity entity = playerContext.entity();
''',
     '''    default void logDirect(Component... components) {
        IEntityContext playerContext = this.getPlayerContext();
        LivingEntity entity = playerContext.entity();
        // SMH: to the server's log as well (plan 71, step 1.7)
        StringBuilder line = new StringBuilder();
        for (Component c : components) line.append(c.getString());
        com.mineagent.sbe.common.ServerHooks.log(entity, line.toString());
'''),
    # Its debug lines, when this body's own chatDebug is on (it read the global one, which nothing sets).
    ('automatone/api/utils/IEntityContext.java',
     '''        if (!BaritoneAPI.getGlobalSettings().chatDebug.get()) {
            return;
        }
''',
     '''        if (!baritone().settings().chatDebug.get()) { // SMH: this body's own (plan 71, step 1.7)
            return;
        }
        com.mineagent.sbe.common.ServerHooks.log(entity(), message); // SMH
'''),
]
