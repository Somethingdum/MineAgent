package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.control.KillAura;
import adris.altoclef.multiversion.versionedfields.Entities;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.construction.ProjectileProtectionWallTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.CustomBaritoneGoalTask;
import adris.altoclef.tasks.movement.DodgeProjectilesTask;
import adris.altoclef.tasks.movement.RunAwayFromCreepersTask;
import adris.altoclef.tasks.movement.RunAwayFromHostilesTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.baritone.CachedProjectile;
import adris.altoclef.util.helpers.*;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import baritone.Baritone;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.*;


// TODO: Optimise shielding against spiders and skeletons

public class MobDefenseChain extends SingleTaskChain {
    private static final double DANGER_KEEP_DISTANCE = 30;
    private static final double CREEPER_KEEP_DISTANCE = 10;
    private static final double ARROW_KEEP_DISTANCE_HORIZONTAL = 2;
    private static final double ARROW_KEEP_DISTANCE_VERTICAL = 10;
    private static final double SAFE_KEEP_DISTANCE = 8;
    private static final List<Class<? extends Entity>> ignoredMobs = List.of(Entities.WARDEN, WitherBoss.class, EnderMan.class, Blaze.class,
            WitherSkeleton.class, Hoglin.class, Zoglin.class, PiglinBrute.class, Vindicator.class, MagmaCube.class);

    private static boolean shielding = false;
    private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
    private final KillAura killAura = new KillAura();
    private Entity targetEntity;
    private boolean doingFunkyStuff = false;
    private boolean wasPuttingOutFire = false;
    private CustomBaritoneGoalTask runAwayTask;
    private float prevHealth = 20;
    private boolean needsChangeOnAttack = false;
    private Entity lockedOnEntity = null;

    private float cachedLastPriority;

    public MobDefenseChain(TaskRunner runner) {
        super(runner);
    }

    public static double getCreeperSafety(Vec3 pos, Creeper creeper) {
        double distance = creeper.distanceToSqr(pos);
        float fuse = creeper.getSwelling(1);

        // Not fusing.
        if (fuse <= 0.001f) return distance;
        return distance * 0.2; // less is WORSE
    }

    private static void startShielding(AltoClef mod) {
        shielding = true;
        mod.getClientBaritone().getPathingBehavior().requestPause();
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
        if (!mod.getPlayer().isBlocking()) {
            ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
            if (ItemVer.isFood(handItem)) {
                List<ItemStack> spaceSlots = mod.getItemStorage().getItemStacksPlayerInventory(false);
                for (ItemStack spaceSlot : spaceSlots) {
                    if (spaceSlot.isEmpty()) {
                        mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(), 0, ClickType.QUICK_MOVE);
                        return;
                    }
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
            }
        }
        mod.getInputControls().hold(Input.SNEAK);
        mod.getInputControls().hold(Input.CLICK_RIGHT);
    }

    private static int getDangerousnessScore(List<LivingEntity> toDealWithList) {
        int numberOfProblematicEntities = toDealWithList.size();
        for (LivingEntity toDealWith : toDealWithList) {
            if (toDealWith instanceof EnderMan || toDealWith instanceof Slime || toDealWith instanceof Blaze) {

                numberOfProblematicEntities += 1;
            } else if (toDealWith instanceof Drowned && toDealWith.getAllSlots() == Items.TRIDENT) {
                // Drowned with tridents are also REALLY dangerous, maybe we should increase this??
                numberOfProblematicEntities += 5;
            }
        }
        return numberOfProblematicEntities;
    }

    @Override
    public float getPriority() {
        cachedLastPriority = getPriorityInner();
        prevHealth = AltoClef.getInstance().getPlayer().getHealth();
        return cachedLastPriority;
    }

    private void stopShielding(AltoClef mod) {
        if (shielding) {
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (ItemVer.isFood(cursor)) {
                Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
                if (toMoveTo.isPresent()) {
                    Slot garbageSlot = toMoveTo.get();
                    mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
                }
            }
            mod.getInputControls().release(Input.SNEAK);
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            shielding = false;
        }
    }

    public boolean isShielding() {
        return shielding || killAura.isShielding();
    }

    private boolean escapeDragonBreath(AltoClef mod) {
        dragonBreathTracker.updateBreath(mod);
        for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer()) {
            if (dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                return true;
            }
        }
        return false;
    }

    private float getPriorityInner() {
        if (!AltoClef.inGame()) {
            return Float.NEGATIVE_INFINITY;
        }
        AltoClef mod = AltoClef.getInstance();

        if (!mod.getModSettings().isMobDefense()) {
            return Float.NEGATIVE_INFINITY;
        }

        if (mod.getWorld().getDifficulty() == Difficulty.PEACEFUL) return Float.NEGATIVE_INFINITY;

        if (needsChangeOnAttack && (mod.getPlayer().getHealth() < prevHealth || killAura.attackedLastTick)) {
            needsChangeOnAttack = false;
        }

        // Put out fire if we're standing on one like an idiot
        BlockPos fireBlock = isInsideFireAndOnFire(mod);
        if (fireBlock != null) {
            putOutFire(mod, fireBlock);
            wasPuttingOutFire = true;
        } else {
            // Stop putting stuff out if we no longer need to put out a fire.
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            wasPuttingOutFire = false;
        }

        // Run away if a weird mob is close by.
        Optional<Entity> universallyDangerous = getUniversallyDangerousMob(mod);
        if (universallyDangerous.isPresent() && mod.getPlayer().getHealth() <= 10) {
            runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
            setTask(runAwayTask);
            return 70;
        }

        doingFunkyStuff = false;
        PlayerSlot offhandSlot = PlayerSlot.OFFHAND_SLOT;
        Item offhandItem = StorageHelper.getItemStackInSlot(offhandSlot).getItem();
        // Run away from creepers
        Creeper blowingUp = getClosestFusingCreeper(mod);
        if (blowingUp != null) {
            if ((!mod.getFoodChain().needsToEat() || mod.getPlayer().getHealth() < 9)
                    && hasShield(mod)
                    && !mod.getEntityTracker().entityFound(ThrownPotion.class)
                    && !mod.getPlayer().getCooldowns().isOnCooldown(offhandItem)
                    && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()
                    && blowingUp.getSwelling(blowingUp.getSwellDir()) > 0.5) {
                LookHelper.lookAt(mod, blowingUp.getEyePosition());
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    startShielding(mod);
                }
            } else {
                doingFunkyStuff = true;
                runAwayTask = new RunAwayFromCreepersTask(CREEPER_KEEP_DISTANCE);
                setTask(runAwayTask);
                return 50 + blowingUp.getSwelling(1) * 50;
            }
        }
        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            // Block projectiles with shield
            if (mod.getModSettings().isDodgeProjectiles()
                    && hasShield(mod)
                    && !mod.getPlayer().getCooldowns().isOnCooldown(offhandItem)
                    && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()
                    && !mod.getEntityTracker().entityFound(ThrownPotion.class) && isProjectileClose(mod)) {
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    startShielding(mod);
                }
                return 60;
            }
            if (blowingUp == null && !isProjectileClose(mod)) {
                stopShielding(mod);
            }
        }

        if (mod.getFoodChain().needsToEat() || mod.getMLGBucketChain().isFalling(mod)
                || !mod.getMLGBucketChain().doneMLG() || mod.getMLGBucketChain().isChorusFruiting()) {
            killAura.stopShielding(mod);
            stopShielding(mod);
            return Float.NEGATIVE_INFINITY;
        }

        // Force field
        doForceField(mod);

        // Dodge projectiles
        if (mod.getPlayer().getHealth() <= 10 && !hasShield(mod)) {

            if (StorageHelper.getNumberOfThrowawayBlocks(mod) > 0 && !mod.getFoodChain().needsToEat()
                    && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod)) {
                doingFunkyStuff = true;
                setTask(new ProjectileProtectionWallTask(mod));
                return 65;
            }

            runAwayTask = new DodgeProjectilesTask(ARROW_KEEP_DISTANCE_HORIZONTAL, ARROW_KEEP_DISTANCE_VERTICAL);
            setTask(runAwayTask);
            return 65;
        }
        // Dodge all mobs cause we boutta die son
        if (isInDanger(mod) && !escapeDragonBreath(mod) && !mod.getFoodChain().isShouldStop()) {
            if (targetEntity == null || WorldHelper.isSurroundedByHostiles()) {
                runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
                setTask(runAwayTask);
                return 70;
            }
        }

        if (mod.getModSettings().shouldDealWithAnnoyingHostiles()) {
            // Deal with hostiles because they are annoying.
            List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();

            List<LivingEntity> toDealWithList = new ArrayList<>();

            synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                for (LivingEntity hostile : hostiles) {
                    boolean isRangedOrPoisonous = (hostile instanceof Skeleton
                            || hostile instanceof Witch || hostile instanceof Pillager
                            || hostile instanceof Piglin || hostile instanceof Stray
                            || hostile instanceof CaveSpider);
                    int annoyingRange = 10;

                    if (isRangedOrPoisonous) {
                        annoyingRange = 20;
                        if (!hasShield(mod)) {
                            annoyingRange = 35;
                        }
                    }

                    // Give each hostile a timer, if they're close for too long deal with them.
                    if (hostile.closerThan(mod.getPlayer(), annoyingRange) && LookHelper.seesPlayer(hostile, mod.getPlayer(), annoyingRange)) {

                        boolean isIgnored = false;
                        for (Class<? extends Entity> ignored : ignoredMobs) {
                            if (ignored.isInstance(hostile)) {
                                isIgnored = true;
                                break;
                            }
                        }

                        // do not go and "attack" these mobs, just hit them if on low HP, or they are close
                        if (isIgnored) {
                            if (mod.getPlayer().getHealth() <= 10) {
                                toDealWithList.add(hostile);
                            }
                        } else {
                            toDealWithList.add(hostile);
                        }
                    }
                }
            }

            // attack entities closest to the player first
            toDealWithList.sort(Comparator.comparingDouble((entity) -> mod.getPlayer().distanceTo(entity)));

            if (!toDealWithList.isEmpty()) {

                // Depending on our weapons/armor, we may choose to straight up kill hostiles if we're not dodging their arrows.
                SwordItem bestSword = getBestSword(mod);

                int armor = mod.getPlayer().getArmorValue();
                float damage = bestSword == null ? 0 : (bestSword.getTier().getAttackDamageBonus()) + 1;

                int shield = hasShield(mod) && bestSword != null ? 3 : 0;

                int canDealWith = (int) Math.ceil((armor * 3.6 / 20.0) + (damage * 0.8) + (shield));

                if (canDealWith >= getDangerousnessScore(toDealWithList) || needsChangeOnAttack) {
                    // we just decided to attack, so we should either get it, or hit something before running away again
                    if (!(mainTask instanceof KillEntitiesTask)) {
                        needsChangeOnAttack = true;
                    }

                    // We can deal with it.
                    runAwayTask = null;
                    Entity toKill = toDealWithList.get(0);
                    lockedOnEntity = toKill;

                    setTask(new KillEntitiesTask(toKill.getClass()));
                    return 65;
                } else {
                    // We can't deal with it
                    runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
                    setTask(runAwayTask);
                    return 80;
                }
            }
        }
        // By default, if we aren't "immediately" in danger but were running away, keep
        // running away until we're good.
        if (runAwayTask != null && !runAwayTask.isFinished()) {
            setTask(runAwayTask);
            return cachedLastPriority;
        } else {
            runAwayTask = null;
        }

        if (needsChangeOnAttack && lockedOnEntity != null && lockedOnEntity.isAlive()) {
            setTask(new KillEntitiesTask(lockedOnEntity.getClass()));
            return 65;
        } else {
            needsChangeOnAttack = false;
            lockedOnEntity = null;
        }

        return 0;
    }

    private static boolean hasShield(AltoClef mod) {
        return mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(Items.SHIELD);
    }

    private static SwordItem getBestSword(AltoClef mod) {
        Item[] SWORDS = new Item[]{Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD,
                Items.STONE_SWORD, Items.WOODEN_SWORD};

        SwordItem bestSword = null;
        for (Item item : SWORDS) {
            if (mod.getItemStorage().hasItem(item)) {
                bestSword = (SwordItem) item;
                break;
            }
        }
        return bestSword;
    }

    private BlockPos isInsideFireAndOnFire(AltoClef mod) {
        boolean onFire = mod.getPlayer().isOnFire();
        if (!onFire) return null;
        BlockPos p = mod.getPlayer().blockPosition();
        BlockPos[] toCheck = new BlockPos[]{
                p,
                p.offset(1,0,0),
                p.offset(1,0,-1),
                p.offset(0,0,-1),
                p.offset(-1,0,-1),
                p.offset(-1,0,0),
                p.offset(-1,0,1),
                p.offset(0,0,1),
                p.offset(1,0,1)
        };
        for (BlockPos check : toCheck) {
            Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b instanceof BaseFireBlock) {
                return check;
            }
        }
        return null;
    }

    private void putOutFire(AltoClef mod, BlockPos pos) {
        Optional<Rotation> reach = LookHelper.getReach(pos);
        if (reach.isPresent()) {
            Baritone b = mod.getClientBaritone();
            if (LookHelper.isLookingAt(mod, pos)) {
                b.getPathingBehavior().requestPause();
                b.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                return;
            }
            LookHelper.lookAt(reach.get());
        }
    }

    private void doForceField(AltoClef mod) {
        killAura.tickStart();

        // Hit all hostiles close to us.
        List<Entity> entities = mod.getEntityTracker().getCloseEntities();
        try {
            for (Entity entity : entities) {
                boolean shouldForce = false;
                if (mod.getBehaviour().shouldExcludeFromForcefield(entity)) continue;
                if (entity instanceof Mob) {
                    if (EntityHelper.isProbablyHostileToPlayer(mod, entity)) {
                        if (LookHelper.seesPlayer(entity, mod.getPlayer(), 10)) {
                            shouldForce = true;
                        }
                    }
                } else if (entity instanceof LargeFireball) {
                    // Ghast ball
                    shouldForce = true;
                }

                if (shouldForce) {
                    killAura.applyAura(entity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        killAura.tickEnd(mod);
    }


    private Creeper getClosestFusingCreeper(AltoClef mod) {
        double worstSafety = Float.POSITIVE_INFINITY;
        Creeper target = null;
        try {
            List<Creeper> creepers = mod.getEntityTracker().getTrackedEntities(Creeper.class);
            for (Creeper creeper : creepers) {
                if (creeper == null) continue;
                if (creeper.getSwelling(1) < 0.001) continue;

                // We want to pick the closest creeper, but FIRST pick creepers about to blow
                // At max fuse, the cost goes to basically zero.
                double safety = getCreeperSafety(mod.getPlayer().position(), creeper);
                if (safety < worstSafety) {
                    target = creeper;
                }
            }
        } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            // IDK why but these exceptions happen sometimes. It's extremely bizarre and I
            // have no idea why.
            Debug.logWarning("Weird Exception caught and ignored while scanning for creepers: " + e.getMessage());
            return target;
        }
        return target;
    }

    private boolean isProjectileClose(AltoClef mod) {
        List<CachedProjectile> projectiles = mod.getEntityTracker().getProjectiles();
        try {
            for (CachedProjectile projectile : projectiles) {
                if (projectile.position.distanceToSqr(mod.getPlayer().position()) < 150) {
                    boolean isGhastBall = projectile.projectileType == LargeFireball.class;
                    if (isGhastBall) {
                        Optional<Entity> ghastBall = mod.getEntityTracker().getClosestEntity(LargeFireball.class);
                        Optional<Entity> ghast = mod.getEntityTracker().getClosestEntity(Ghast.class);
                        if (ghastBall.isPresent() && ghast.isPresent() && runAwayTask == null
                                && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()) {
                            mod.getClientBaritone().getPathingBehavior().requestPause();
                            LookHelper.lookAt(mod, ghast.get().getEyePosition());
                        }
                        return false;
                        // Ignore ghast balls
                    }
                    if (projectile.projectileType == DragonFireball.class) {
                        // Ignore dragon fireballs
                        continue;
                    }
                    if (projectile.projectileType == Arrow.class || projectile.projectileType == SpectralArrow.class || projectile.projectileType == SmallFireball.class) {
                        // check if the projectile is going away from us
                        // not so fancy math... this should work better than the previous approach (I hope just adding the velocity doesn't cause any issues..)
                        Player player = mod.getPlayer();
                        if (player.distanceToSqr(projectile.position) < player.distanceToSqr(projectile.position.add(projectile.velocity))) {
                            continue;
                        }
                    }

                    Vec3 expectedHit = ProjectileHelper.calculateArrowClosestApproach(projectile, mod.getPlayer());

                    Vec3 delta = mod.getPlayer().position().subtract(expectedHit);

                    double horizontalDistanceSq = delta.x * delta.x + delta.z * delta.z;
                    double verticalDistance = Math.abs(delta.y);
                    if (horizontalDistanceSq < ARROW_KEEP_DISTANCE_HORIZONTAL * ARROW_KEEP_DISTANCE_HORIZONTAL
                            && verticalDistance < ARROW_KEEP_DISTANCE_VERTICAL) {
                        if (mod.getClientBaritone().getPathingBehavior().isSafeToCancel()
                                && hasShield(mod)) {
                            mod.getClientBaritone().getPathingBehavior().requestPause();
                            LookHelper.lookAt(mod, projectile.position.add(0, 0.3, 0));
                        }
                        return true;
                    }
                }
            }

        } catch (ConcurrentModificationException e) {
            Debug.logWarning(e.getMessage());
        }

        // TODO refactor this into something more reliable for all mobs
        for (Skeleton skeleton : mod.getEntityTracker().getTrackedEntities(Skeleton.class)) {
            if (skeleton.distanceTo(mod.getPlayer()) > 10 || !skeleton.hasLineOfSight(mod.getPlayer())) continue;

            // when the skeleton is about to shoot (it takes 5 ticks to raise the shield)
            if (skeleton.getTicksUsingItem() > 15) {
                return true;
            }
        }

        return false;
    }

    private Optional<Entity> getUniversallyDangerousMob(AltoClef mod) {
        // Wither skeletons are dangerous because of the wither effect. Oof kinda obvious.
        // If we merely force field them, we will run into them and get the wither effect which will kill us.

        Class<?>[] dangerousMobs = new Class[]{Entities.WARDEN, WitherBoss.class, WitherSkeleton.class,
                Hoglin.class, Zoglin.class, PiglinBrute.class, Vindicator.class};

        double range = SAFE_KEEP_DISTANCE - 2;

        for (Class<?> dangerous : dangerousMobs) {
            Optional<Entity> entity = mod.getEntityTracker().getClosestEntity(dangerous);

            if (entity.isPresent()) {
                if (entity.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, entity.get())) {
                    return entity;
                }
            }
        }

        return Optional.empty();
    }

    private boolean isInDanger(AltoClef mod) {
        boolean witchNearby = mod.getEntityTracker().entityFound(Witch.class);

        float health = mod.getPlayer().getHealth();
        if (health <= 10 && !witchNearby) {
            return true;
        }
        if (mod.getPlayer().hasEffect(MobEffects.WITHER) ||
                (mod.getPlayer().hasEffect(MobEffects.POISON) && !witchNearby)) {
            return true;
        }
        if (WorldHelper.isVulnerable()) {
            // If hostile mobs are nearby...
            try {
                LocalPlayer player = mod.getPlayer();
                List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();

                synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                    for (Entity entity : hostiles) {
                        if (entity.closerThan(player, SAFE_KEEP_DISTANCE)
                                && !mod.getBehaviour().shouldExcludeFromForcefield(entity)
                                && EntityHelper.isAngryAtPlayer(mod, entity)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Debug.logWarning("Weird multithread exception. Will fix later. " + e.getMessage());
            }
        }
        return false;
    }

    public void setTargetEntity(Entity entity) {
        targetEntity = entity;
    }

    public void resetTargetEntity() {
        targetEntity = null;
    }

    public void setForceFieldRange(double range) {
        killAura.setRange(range);
    }

    public void resetForceField() {
        killAura.setRange(Double.POSITIVE_INFINITY);
    }

    public boolean isDoingAcrobatics() {
        return doingFunkyStuff;
    }

    public boolean isPuttingOutFire() {
        return wasPuttingOutFire;
    }

    @Override
    public boolean isActive() {
        // We're always checking for mobs
        return true;
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        // Task is done, so I guess we move on?
    }

    @Override
    public String getName() {
        return "Mob Defense";
    }
}