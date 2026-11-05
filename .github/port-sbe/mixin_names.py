# The ported mixins' annotation names: Loom's remap translated SecondBrainEngine's code, not the strings in its mixin
# annotations (still Yarn's names), so here they are given Mojang's names for 1.21.1. (file, old, new), as port.py's.
MIXIN_PATCHES = [
    ('mixins/ClientConnectionAccessor.java', '@Accessor("ticks")', '@Accessor("tickCount") // SMH: Mojang\'s name'),
    ('mixins/EntityAnimationSwungMixin.java', '"swingHand(Lnet/minecraft/util/Hand;)V"',
     '"swing(Lnet/minecraft/world/InteractionHand;)V" /* SMH: Mojang\'s name */'),
    ('mixins/LivingEntityMixin.java', '@Accessor("lastAttackedTicks")',
     '@Accessor("attackStrengthTicker") // SMH: Mojang\'s name'),
    ('mixins/MixinAbstractFurnaceBlockEntity.java', '@Accessor("propertyDelegate")',
     '@Accessor("dataAccess") // SMH: Mojang\'s name'),
    ('mixins/MixinItemStack.java', '    @Shadow\n    public abstract int getDamage();',
     '    @Shadow\n    public abstract int getDamageValue(); // SMH: Mojang\'s name'),
    ('mixins/MixinItemStack.java', 'item.hashCode() + getDamage();', 'item.hashCode() + getDamageValue();'),
    ('mixins/MixinItemStack.java', 'method = "setDamage",', 'method = "setDamageValue", // SMH: Mojang\'s name'),
    ('mixins/MixinLivingEntity.java', '@Invoker("getNextAirUnderwater")',
     '@Invoker("decreaseAirSupply") // SMH: Mojang\'s name'),
    ('mixins/MixinLivingEntity.java', '@Invoker("getNextAirOnLand")',
     '@Invoker("increaseAirSupply") // SMH: Mojang\'s name'),
    ('mixins/MixinMobEntity.java', '@Inject(method = "tickNewAi"',
     '@Inject(method = "serverAiStep" /* SMH: Mojang\'s name */'),
    ('mixins/MixinServerChunkManager.java', 'protected abstract ChunkHolder getChunkHolder(long pos);',
     'protected abstract ChunkHolder getVisibleChunkIfPresent(long pos); // SMH: Mojang\'s name'),
    ('mixins/MixinServerChunkManager.java', 'this.getChunkHolder(ChunkPos.asLong(chunkX, chunkZ))',
     'this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ))'),
    ('mixins/MixinServerCommandSource.java', '@Accessor("output")', '@Accessor("source") // SMH: Mojang\'s name'),
    ('mixins/MixinServerPlayerInteractionManager.java',
     '    @Override\n    @Accessor\n    public abstract boolean isMining();\n'
     '    @Override\n    @Accessor\n    public abstract BlockPos getMiningPos();\n'
     '    @Accessor\n    public abstract int getBlockBreakingProgress();',
     '    // SMH: Mojang\'s field names (Yarn\'s mining, miningPos and blockBreakingProgress).\n'
     '    @Override\n    @Accessor("isDestroyingBlock")\n    public abstract boolean isMining();\n'
     '    @Override\n    @Accessor("destroyPos")\n    public abstract BlockPos getMiningPos();\n'
     '    @Accessor("lastSentState")\n    public abstract int getBlockBreakingProgress();'),
    ('mixins/MixinServerPlayerInteractionManager.java',
     '@Inject(method = "processBlockBreakingAction", at = @At(value = "FIELD", target = '
     '"Lnet/minecraft/server/network/ServerPlayerInteractionManager;miningPos:Lnet/minecraft/util/math/BlockPos;", '
     'opcode = Opcodes.PUTFIELD))',
     '@Inject(method = "handleBlockBreakAction", at = @At(value = "FIELD", target = '
     '"Lnet/minecraft/server/level/ServerPlayerGameMode;destroyPos:Lnet/minecraft/core/BlockPos;", '
     'opcode = Opcodes.PUTFIELD)) // SMH: Mojang\'s names'),
    ('mixins/PlayerCollidesWithEntityMixin.java', 'method = {"collideWithEntity"},',
     'method = {"touch"}, // SMH: Mojang\'s name'),
    ('mixins/PlayerCollidesWithEntityMixin.java',
     'target = "Lnet/minecraft/entity/Entity;onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V"',
     'target = "Lnet/minecraft/world/entity/Entity;playerTouch(Lnet/minecraft/world/entity/player/Player;)V" // SMH: Mojang\'s names'),
    ('mixins/PlayerDamageMixin.java', 'method = {"damage"},', 'method = {"hurt"}, // SMH: Mojang\'s name'),
    ('mixins/WorldBlockModifiedMixin.java', 'method = {"onBlockChanged"},',
     'method = {"onBlockStateChange"}, // SMH: Mojang\'s name'),
]
