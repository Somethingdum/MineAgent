# SMH plan 71, step 1.5 (§5.3, ledger 2): AltoClef's own patches to Baritone (MiranCZ/altoclef's
# research/baritone_altoclef/patches 0002 "AltoClef changes" and 0003 "BuilderProcess changes", LGPL-3.0 as Baritone is;
# third_party/baritone_altoclef), put onto Automatone. AltoClef asks one static AltoClefSettings.getInstance(); here
# every place asks its own companion's (Baritone.getExtraBaritoneSettings()), since Automatone runs one Baritone per
# body and plans on executor threads that must read the companion they are planning for. The rules ride on each
# BlockStateInterface (every path search makes its own, for one Baritone); a place with an entity context asks its
# Baritone. Each entry: (file, old, new), the old text found once in the file (port.py), and a comment naming the hunk.
# What 0002 and 0003 change that is not taken is listed at the end, each with why (AUTOMATONE-LEDGER.md, ledger 2).
R = 'me.sailex.automatone.settings.AltoClefSettings'
B = 'me.sailex.automatone.Baritone'
FRAME = 'net.minecraft.world.level.block.EndPortalFrameBlock'
NOTE = ' // SMH: AltoClef 0002'


def of(baritone):
    """The companion's own AltoClef rules, from an IBaritone expression."""
    return '((' + B + ') ' + baritone + ').getExtraBaritoneSettings()'


HOOKS = [
    # The rules on each BlockStateInterface: the companion's when made for one, none (nothing avoided) otherwise.
    ('automatone/utils/BlockStateInterface.java',
     '    public BlockStateInterface(IEntityContext ctx) {\n        this(ctx.world());\n    }\n\n'
     '    public BlockStateInterface(Level world) {\n        this.world = world;',
     '    // SMH: the companion\'s own AltoClef rules (plan 71, step 1.5), asked from the path threads; none when made\n'
     '    // for no companion.\n'
     '    public final ' + R + ' rules;\n'
     '    private static final ' + R + ' NO_RULES = new ' + R + '();\n\n'
     '    public BlockStateInterface(IEntityContext ctx) {\n        this(ctx.world(), ' + of('ctx.baritone()') + ');\n    }\n\n'
     '    public BlockStateInterface(Level world) {\n        this(world, NO_RULES);\n    }\n\n'
     '    public BlockStateInterface(Level world, ' + R + ' rules) {\n        this.rules = rules;\n        this.world = world;'),

    # 0002, CalculationContext: its search reads this companion's rules; nothing broken or placed while AltoClef holds
    # interaction; its rules priced as never (breaking an end portal frame too).
    ('automatone/pathing/movement/CalculationContext.java',
     '        this.bsi = new BlockStateInterface(world);',
     '        this.bsi = new BlockStateInterface(world, ((Baritone) baritone).getExtraBaritoneSettings()); // SMH: its rules'),
    ('automatone/pathing/movement/CalculationContext.java',
     '        this.hasThrowaway = baritone.settings().allowPlace.get()',
     '        this.hasThrowaway = !bsi.rules.isInteractionPaused() && baritone.settings().allowPlace.get()'),
    ('automatone/pathing/movement/CalculationContext.java',
     '        this.allowBreak = baritone.settings().allowBreak.get();',
     '        this.allowBreak = !bsi.rules.isInteractionPaused() && baritone.settings().allowBreak.get();' + NOTE),
    ('automatone/pathing/movement/CalculationContext.java',
     '        if (isProtected(x, y, z)) {\n            return COST_INF;\n        }\n        return placeBlockCost;',
     '        if (isProtected(x, y, z)) {\n            return COST_INF;\n        }\n'
     '        if (bsi.rules.shouldAvoidPlacingAt(x, y, z)) {' + NOTE + '\n            return COST_INF;\n        }\n'
     '        return placeBlockCost;'),
    ('automatone/pathing/movement/CalculationContext.java',
     '        if (isProtected(x, y, z)) {\n            return COST_INF;\n        }\n        return 1;',
     '        if (isProtected(x, y, z)) {\n            return COST_INF;\n        }\n'
     '        if (bsi.rules.shouldAvoidBreaking(x, y, z) || current.getBlock() instanceof ' + FRAME + ') {' + NOTE + '\n'
     '            return COST_INF;\n        }\n'
     '        return 1;'),

    # 0002, MovementHelper.avoidBreaking: its rules, an end portal frame, and the block under one.
    ('automatone/pathing/movement/MovementHelper.java',
     '    static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {\n'
     '        Block b = state.getBlock();\n        return b == Blocks.ICE',
     '    static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {\n'
     '        if (bsi.get0(x, y + 1, z).getBlock() instanceof ' + FRAME + ') {' + NOTE + '\n            return true;\n        }\n'
     '        if (bsi.rules.shouldAvoidBreaking(x, y, z)) {\n            return true;\n        }\n'
     '        Block b = state.getBlock();\n        return b instanceof ' + FRAME + '\n                || b == Blocks.ICE'),
    # 0002, canWalkThrough: lava it may swim (nothing on top), and the cells its rules keep it out of. Before the air
    # check: a cell kept clear is often air (AltoClef's dragon's breath).
    ('automatone/pathing/movement/MovementHelper.java',
     '    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {\n'
     '        Block block = state.getBlock();\n',
     '    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {\n'
     '        Block block = state.getBlock();\n'
     '        if (bsi.rules.canSwimThroughLava() && block == Blocks.LAVA) {' + NOTE + '\n'
     '            return bsi.get0(x, y + 1, z).getFluidState().isEmpty();\n        }\n'
     '        if (bsi.rules.shouldAvoidWalkThroughForce(x, y, z)) {\n            return false;\n        }\n'),
    # 0002, fullyPassable (the executor's own check, with its entity).
    ('automatone/pathing/movement/MovementHelper.java',
     '    static boolean fullyPassable(IEntityContext ctx, BlockPos pos) {\n        return fullyPassable(',
     '    static boolean fullyPassable(IEntityContext ctx, BlockPos pos) {\n'
     '        if (' + of('ctx.baritone()') + '.shouldAvoidWalkThroughForce(pos)) {' + NOTE + '\n            return false;\n        }\n'
     '        return fullyPassable('),
    # 0002, canWalkOnBlockState: an end portal frame is ground, and an end portal is when AltoClef says so. Automatone
    # has no state-only cache, so it goes in canWalkOn, where its rules are in reach.
    ('automatone/pathing/movement/MovementHelper.java',
     '    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {\n'
     '        Block block = state.getBlock();\n',
     '    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {\n'
     '        Block block = state.getBlock();\n'
     '        if (block instanceof ' + FRAME + ' || (block == Blocks.END_PORTAL && bsi.rules.isCanWalkOnEndPortal())) {' + NOTE + '\n'
     '            return true;\n        }\n'),
    # 0002, avoidWalkingInto: an end portal frame is walked round.
    ('automatone/pathing/movement/MovementHelper.java',
     '                || block == Blocks.END_PORTAL\n                || block == Blocks.COBWEB',
     '                || block instanceof ' + FRAME + NOTE + '\n                || block == Blocks.END_PORTAL\n                || block == Blocks.COBWEB'),
    # 0002, canPlaceAgainst: nothing placed where its rules say not.
    ('automatone/pathing/movement/MovementHelper.java',
     '    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, BlockState state) {\n',
     '    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, BlockState state) {\n'
     '        if (bsi.rules.shouldAvoidPlacingAt(x, y, z)) {' + NOTE + '\n            return false;\n        }\n'),

    # 0002, the movements' own checks while they run (entity context): descend's overshoot, traverse's walking into
    # what it is still breaking, fall's bucket, and the executor's corner cut.
    ('automatone/pathing/movement/movements/MovementDescend.java',
     '            if (MovementHelper.avoidWalkingInto(state)\n'
     '                    && !(MovementHelper.isWater(state) && baritone.settings().allowSwimming.get())) {\n'
     '                return true;\n            }\n',
     '            if (MovementHelper.avoidWalkingInto(state)\n'
     '                    && !(MovementHelper.isWater(state) && baritone.settings().allowSwimming.get())) {\n'
     '                return true;\n            }\n'
     '            if (' + of('baritone') + '.shouldAvoidWalkThroughForce(into.above(y))) {' + NOTE + '\n                return true;\n            }\n'),
    ('automatone/pathing/movement/movements/MovementTraverse.java',
     '            for (BlockState pb : bss) {\n                if (MovementHelper.avoidWalkingInto(pb)) {\n                    return state;\n                }\n            }\n',
     '            for (BlockState pb : bss) {\n                if (MovementHelper.avoidWalkingInto(pb)) {\n                    return state;\n                }\n            }\n'
     '            for (BlockPos p : positionsToBreak) {\n'
     '                if (' + of('baritone') + '.shouldAvoidWalkThroughForce(p)) {' + NOTE + '\n                    return state;\n                }\n            }\n'),
    ('automatone/pathing/movement/movements/MovementFall.java',
     'willPlaceBucket() && !playerFeet.equals(dest)) {',
     'willPlaceBucket() && !playerFeet.equals(dest) && !' + of('baritone') + '.shouldNotPlaceBucketButStillFall()) {' + NOTE),
    ('automatone/pathing/path/PathExecutor.java',
     '        if (MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().above(3)))) {\n'
     '            return false;\n        }\n',
     '        if (MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().above(3)))) {\n'
     '            return false;\n        }\n'
     '        if (' + of('ctx.baritone()') + '.shouldAvoidWalkThroughForce(current.getSrc().above(3))\n'
     '                || ' + of('ctx.baritone()') + '.shouldAvoidWalkThroughForce(current.getSrc().above(2))) {' + NOTE + '\n'
     '            return false;\n        }\n'),

    # 0002, MineProcess: it waits while AltoClef holds interaction; the cache is widened by the number of kinds asked
    # for (AltoClef's fix: one count for all of them stopped too early); lava and end portals count as plausible.
    ('automatone/process/MineProcess.java',
     '        updateLoucaSystem();\n        int mineGoalUpdateInterval',
     '        if (' + of('baritone') + '.isInteractionPaused()) {' + NOTE + '\n'
     '            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);\n        }\n'
     '        updateLoucaSystem();\n        int mineGoalUpdateInterval'),
    ('automatone/process/MineProcess.java',
     'extendCacheOnThreshold.get() && locs.size() < max)) {',
     'extendCacheOnThreshold.get() && locs.size() < max * filter.blocks().size())) {' + NOTE),
    ('automatone/process/MineProcess.java',
     '    public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {\n',
     '    public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {\n'
     '        net.minecraft.world.level.block.Block here = ctx.bsi.get0(pos).getBlock();' + NOTE + '\n'
     '        if (here instanceof ' + FRAME + ' || here instanceof net.minecraft.world.level.block.EndPortalBlock || here == Blocks.LAVA) {\n'
     '            return true;\n        }\n'),

    # 0002, InputOverrideHandler: no clicks while AltoClef holds interaction.
    ('automatone/utils/InputOverrideHandler.java',
     '        if (!this.needsUpdate) return;\n\n        if (isInputForcedDown(Input.CLICK_LEFT)) {',
     '        if (!this.needsUpdate) return;\n\n'
     '        if (baritone.getExtraBaritoneSettings().isInteractionPaused()) {' + NOTE + '\n'
     '            setInputForceState(Input.CLICK_LEFT, false);\n            setInputForceState(Input.CLICK_RIGHT, false);\n        }\n'
     '        if (isInputForcedDown(Input.CLICK_LEFT)) {'),

    # 0002, ToolSet: a block that breaks at once is broken with what is in hand; no tool it is saving; the one it is
    # told to use.
    ('automatone/utils/ToolSet.java',
     '        if (baritone.settings().disableAutoTool.get() && pathingCalculation) {',
     '        if (b.getExplosionResistance() == 0) {' + NOTE + '\n'
     '            return PlayerInventoryVer.getSelectedSlot(player.getInventory());\n        }\n'
     '        if (baritone.settings().disableAutoTool.get() && pathingCalculation) {'),
    ('automatone/utils/ToolSet.java',
     '            double speed = calculateSpeedVsBlock(itemStack, blockState, player);\n',
     '            if (' + of('baritone') + '.shouldForceSaveTool(blockState, itemStack)) {' + NOTE + '\n                continue;\n            }\n'
     '            double speed = calculateSpeedVsBlock(itemStack, blockState, player);\n'
     '            if (' + of('baritone') + '.shouldForceUseTool(blockState, itemStack)) {\n'
     '                speed = Double.POSITIVE_INFINITY;\n            }\n'),

    # 0002, InventoryBehavior: no moves while AltoClef holds interaction; protected items are never thrown down as
    # blocks; no block chosen for a spot its rules keep clear.
    ('automatone/behavior/InventoryBehavior.java',
     '        if (!baritone.settings().allowInventory.get()) {\n            return;\n        }\n        Player player = ctx.entity();',
     '        if (!baritone.settings().allowInventory.get() || baritone.getExtraBaritoneSettings().isInteractionPaused()) {' + NOTE + '\n'
     '            return;\n        }\n        Player player = ctx.entity();'),
    ('automatone/behavior/InventoryBehavior.java',
     '    private void swapWithHotBar(int inInventory, int inHotbar, Inventory inventory) {\n',
     '    private void swapWithHotBar(int inInventory, int inHotbar, Inventory inventory) {\n'
     '        if (baritone.getExtraBaritoneSettings().isInteractionPaused()) {' + NOTE + '\n            return;\n        }\n'),
    ('automatone/behavior/InventoryBehavior.java',
     '            if (baritone.settings().acceptableThrowawayItems.get().contains(invy.get(i).getItem())) {\n                return i;',
     '            if (baritone.settings().acceptableThrowawayItems.get().contains(invy.get(i).getItem())\n'
     '                    && !baritone.getExtraBaritoneSettings().isItemProtected(invy.get(i).getItem())) {' + NOTE + '\n'
     '                return i;'),
    ('automatone/behavior/InventoryBehavior.java',
     '    public boolean hasGenericThrowaway() {\n        return throwaway(false,\n'
     '                stack -> baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem()));',
     '    public boolean hasGenericThrowaway() {\n        return throwaway(false,\n'
     '                stack -> baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem())\n'
     '                        && !baritone.getExtraBaritoneSettings().isItemProtected(stack.getItem()));' + NOTE),
    ('automatone/behavior/InventoryBehavior.java',
     '    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {\n        Player player = ctx.entity();\n',
     '    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {\n        Player player = ctx.entity();\n'
     '        if (baritone.getExtraBaritoneSettings().shouldAvoidPlacingAt(x, y, z)) {' + NOTE + '\n            return false;\n        }\n'),
    ('automatone/behavior/InventoryBehavior.java',
     '        return throwaway(select,\n'
     '                stack -> baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem()));\n'
     '    }\n\n    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {\n',
     '        return throwaway(select,\n'
     '                stack -> baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem())\n'
     '                        && !baritone.getExtraBaritoneSettings().isItemProtected(stack.getItem()));' + NOTE + '\n'
     '    }\n\n    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {\n'
     '        if (baritone.getExtraBaritoneSettings().isInteractionPaused()) {' + NOTE + '\n            return false;\n        }\n'),

    # 0003, BuilderProcess: no placing and no inventory moves while AltoClef holds interaction; no placement plausible
    # for a state that is not there.
    ('automatone/process/BuilderProcess.java',
     '        if (toPlace.isPresent() && isSafeToCancel && ctx.entity().onGround() && ticks <= 0) {',
     '        if (!baritone.getExtraBaritoneSettings().isInteractionPaused() && toPlace.isPresent() && isSafeToCancel'
     ' && ctx.entity().onGround() && ticks <= 0) { // SMH: AltoClef 0003'),
    ('automatone/process/BuilderProcess.java',
     '        if (baritone.settings().allowInventory.get()) {',
     '        if (!baritone.getExtraBaritoneSettings().isInteractionPaused() && baritone.settings().allowInventory.get()) { // SMH: AltoClef 0003'),
    ('automatone/process/BuilderProcess.java',
     '    public boolean placementPlausible(BlockPos pos, BlockState state) {\n',
     '    public boolean placementPlausible(BlockPos pos, BlockState state) {\n'
     '        if (state == null) return false; // SMH: AltoClef 0003\n'),

    # 0002, RayTraceUtils: the fluid handling AltoClef sets is used (SecondBrainEngine kept the field but cast with
    # NONE), and it is the companion's own, not one static all of them write (plan 71, §14, found for step 1.5).
    ('automatone/settings/AltoClefSettings.java',
     '    public boolean isCanWalkOnEndPortal() {',
     '    // SMH: the fluids its ray traces stop at (AltoClef\'s RayTraceUtils.fluidHandling, one per companion here).\n'
     '    private volatile net.minecraft.world.level.ClipContext.Fluid rayFluidHandling = net.minecraft.world.level.ClipContext.Fluid.NONE;\n\n'
     '    public net.minecraft.world.level.ClipContext.Fluid getRayFluidHandling() {\n        return this.rayFluidHandling;\n    }\n\n'
     '    public void setRayFluidHandling(net.minecraft.world.level.ClipContext.Fluid fluidHandling) {\n'
     '        this.rayFluidHandling = fluidHandling == null ? net.minecraft.world.level.ClipContext.Fluid.NONE : fluidHandling;\n    }\n\n'
     '    public boolean isCanWalkOnEndPortal() {'),
    ('automatone/api/utils/RayTraceUtils.java',
     'clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));',
     'clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, fluidHandlingOf(entity), entity));' + NOTE),
    ('automatone/api/utils/RayTraceUtils.java',
     '    public static Vec3 inferSneakingEyePosition(Entity entity) {',
     '    // SMH: the companion\'s own fluid handling; any other entity\'s ray stops at no fluid (never makes a Baritone).\n'
     '    private static ClipContext.Fluid fluidHandlingOf(Entity entity) {\n'
     '        if (entity instanceof net.minecraft.server.level.ServerPlayer player\n'
     '                && me.sailex.automatone.api.BaritoneAPI.getProvider() instanceof me.sailex.automatone.BaritoneProvider provider) {\n'
     '            me.sailex.automatone.api.IBaritone baritone = provider.playerToBaritone.get(player);\n'
     '            if (baritone != null) return ' + of('baritone') + '.getRayFluidHandling();\n'
     '        }\n'
     '        return ClipContext.Fluid.NONE;\n    }\n\n'
     '    public static Vec3 inferSneakingEyePosition(Entity entity) {'),
]

# Not taken, each with why (AUTOMATONE-LEDGER.md, ledger 2):
NOT_TAKEN = [
    ('0002 PathNode', 'applyGlobalHeuristic', 'returns its input in AltoClef itself (its loop is commented out); a hook that does nothing'),
    ('0002 MovementHelper', 'InfestedBlock removed from avoidBreaking', 'kept: SmhRules.neverBreakToPass never breaks one to pass either'),
    ('0002 MovementTraverse', 'soul sand as an ordinary block', 'Automatone\'s traverse has no soul-sand case to switch off'),
    ('0002 MineProcess', 'the Rotation import and wildcard block imports', 'imports only'),
    ('0002 IBuilderProcess, 0003', 'popStack, isFromAltoclefFinished, the state stack', 'nothing in MiranCZ\'s AltoClef calls them'),
    ('0003', 'the break history (getFromHistory, noteRemoval, HistoryInfo)', 'never filled: asked and cleared only'),
    ('0003', 'protectItemOfMissing / stopProtectItemOfMissing, the missing map', 'the map is never filled and the protect half never called'),
    ('0003', 'isDefaultState, PropertyContainer', 'never called'),
    ('0003', 'updateMovement', 'ticks the path\'s current movement a second time on each builder tick: a second writer of the keys (I4)'),
]
