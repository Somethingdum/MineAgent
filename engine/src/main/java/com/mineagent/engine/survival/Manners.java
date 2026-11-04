package com.mineagent.engine.survival;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manners (SMH plan 70, MA10.5).
 * <ul>
 *   <li>What it may take: something a player threw down (the game records the thrower) is theirs to leave where they
 *   want it. It is never gone after, nor picked up by walking over it, unless they hand it over (thrown at it from
 *   close by, looking at it) or ask it to pick things up.</li>
 *   <li>Its line of fire: no shot whose path passes a player who isn't the target.</li>
 * </ul>
 * The switch is {@code behaviour.manners}.
 */
public final class Manners {
    private Manners() {}

    /** Companions asked to pick things up (a collect order), to the game tick the asking holds until. */
    private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();

    /** Called each tick of a pick-up order: for a few ticks, it may take what anyone threw. */
    public static void asked(ServerPlayer self) {
        if (self != null) ASKED.put(self.getUUID(), self.serverLevel().getGameTime() + 5);
    }

    /** What it handed to someone (the item on the ground), to the game tick it is left alone until: theirs now. */
    private static final Map<UUID, Long> HANDED = new ConcurrentHashMap<>();

    /** It threw this to someone: it leaves it for them (five minutes), unless asked to pick things up. */
    public static void handedOver(ItemEntity item, ServerPlayer self) {
        if (item == null || self == null) return;
        long now = self.serverLevel().getGameTime();
        if (HANDED.size() > 512) HANDED.values().removeIf(until -> until < now);
        HANDED.put(item.getUUID(), now + 6_000);
    }

    /**
     * Whether it may go and take this item: nobody threw it, it did (and not to someone), it was handed over, or it
     * was asked to.
     */
    public static boolean mayTake(ItemEntity item, ServerPlayer self) {
        if (item == null) return false;
        if (self == null || !com.mineagent.engine.settings.Switches.manners()) return true;
        Long until = ASKED.get(self.getUUID());
        boolean asked = until != null && until >= self.serverLevel().getGameTime();
        Long theirs = HANDED.get(item.getUUID());
        if (theirs != null && theirs >= self.serverLevel().getGameTime() && !asked) return false;
        Entity thrower = item.getOwner();
        if (thrower == null || thrower.getUUID().equals(self.getUUID())) return true;
        if (!(thrower instanceof Player)) return true;
        if (asked) return true;
        return handedTo(thrower, self);
    }

    /** Thrown at it: the thrower close by and looking at it, as a player hands something over. */
    static boolean handedTo(Entity thrower, ServerPlayer self) {
        if (thrower.level() != self.level() || thrower.distanceTo(self) > 6) return false;
        Vec3 look = thrower.getLookAngle();
        Vec3 to = self.position().add(0, self.getBbHeight() / 2, 0).subtract(thrower.getEyePosition());
        double len = to.length();
        return len < 1.0E-3 || look.dot(to.scale(1 / len)) > 0.9;
    }

    /** Whether a shot from {@code from} to {@code to} passes a player other than {@code self} and {@code target}. */
    public static boolean crossesSomeone(ServerPlayer self, Vec3 from, Vec3 to, Entity target) {
        if (self == null || !com.mineagent.engine.settings.Switches.manners()) return false;
        for (Player p : self.level().players()) {
            if (p == self || p == target || !p.isAlive() || p.isSpectator()) continue;
            if (p.distanceToSqr(from) > from.distanceToSqr(to) + 16) continue;
            if (p.getBoundingBox().inflate(0.5).clip(from, to).isPresent()) return true;
        }
        return false;
    }
}
