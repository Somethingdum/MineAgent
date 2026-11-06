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

# Step 1.8 (plan 71, §6.2, seam B): one writer per key, one break finisher. Not Baritone's code; ours.
SEAM_B = [
    # Rule 2: for a body its AltoClef host drives (ServerHooks.keysByHost), its keys are applied by the host, Automatone's
    # forced keys over AltoClef's own (AltoClef.tick); this handler leaves the walk keys, jump and sneak alone. Its
    # clicks (breaking, placing) stay here. SecondBrainEngine's zeroing of every key on each update is kept for the
    # other bodies (SecondBrainEngine's own AltoClef, which phase 2 removes, leaves the keys to it).
    ('automatone/utils/InputOverrideHandler.java',
     '''        ServerPlayer entity = this.ctx.entity();
        entity.xxa = 0.0F;
        entity.zza = 0.0F;
        entity.setShiftKeyDown(false);
        commandHelper.executeMoveStop();
        commandHelper.executeUnSneak();
''',
     '''        ServerPlayer entity = this.ctx.entity();
        boolean hostKeys = com.mineagent.sbe.common.ServerHooks.keysAreTheHosts(baritone); // SMH: one writer per key
        if (!hostKeys) {
        entity.xxa = 0.0F;
        entity.zza = 0.0F;
        entity.setShiftKeyDown(false);
        commandHelper.executeMoveStop();
        commandHelper.executeUnSneak();
'''),
    ('automatone/utils/InputOverrideHandler.java',
     '''        if (this.isInputForcedDown(Input.SNEAK)) {
//            entity.setSneaking(true);
//            entity.sidewaysSpeed *= 0.3D;
//            entity.forwardSpeed *= 0.3D;
            commandHelper.executeSneak();
        }
''',
     '''        if (this.isInputForcedDown(Input.SNEAK)) {
//            entity.setSneaking(true);
//            entity.sidewaysSpeed *= 0.3D;
//            entity.forwardSpeed *= 0.3D;
            commandHelper.executeSneak();
        }
        } // SMH: the host applies this body's walk keys, jump and sneak
'''),
    # SecondBrainEngine's own water rise (upward speed 0.25 on every update in water; in neither upstream). Plan 71 keeps
    # such a line only if a scene fails without it: the swim and dive scenes pass with it and without it (Automatone's
    # forced jump swims it up), so it is off for a body its host drives and kept for the other bodies.
    ('automatone/utils/InputOverrideHandler.java',
     '''        if (entity.isInWater()) {''',
     '''        if (!hostKeys && entity.isInWater()) { // SMH: not for a body its host drives'''),
    # Rule 3: one break finisher. Automatone decides which block and when it starts or stops; the body's game mode
    # carries the break to its end, as a client's continueDestroyBlock does (FakePlayerGameMode.tick). Automatone sent
    # its own STOP as well; it reports the game mode's state instead (as the old port's BaritonePlayerController did).
    ('automatone/utils/player/PlayerEntityInteractionController.java',
     '''        if (interactionManager.isMining()) {
            int progress = interactionManager.getBlockBreakingProgress();
            if (progress >= 10) {
                this.player.gameMode.handleBlockBreakAction(interactionManager.getMiningPos(),
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side,
                        HeightLimitViewVer.getTopY(ServerPlayerEntityVer.getWorld(this.player)), sequence++);
            }
            return true;
        }
        return false;''',
     '''        // SMH (plan 71, §6.2 rule 3): the game mode finishes the break; this reports whether it is still breaking.
        return interactionManager.isMining();'''),
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
