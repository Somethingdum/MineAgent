package com.mineagent.engine.combat;

import com.mineagent.engine.combat.CombatBrain.Hostile;
import com.mineagent.engine.combat.CombatBrain.Self;

import java.util.List;
import java.util.Set;

/**
 * Whom to shoot, if anyone (SMH plan 70, "clutch with a bow"): with a bow and arrows, a hostile it can see between 5 and
 * 40 blocks out (a creeper not inside 6) that is coming for someone, closing, or one best fought from range; the most
 * dangerous first, a hissing creeper above all. Nothing when something other than a creeper is already at arm's length
 * (the sword's turn), in water (arrows slow to nothing there), or at what arrows can't land on: an enderman teleports
 * out of their way, a breeze turns them aside, and the warden, the wither and the dragon are fled, not fought. Pure.
 */
public final class RangedChoice {
    private RangedChoice() {}

    public static final double MIN = 5.0, CREEPER_MIN = 6.0, MAX = 40.0, CLOSE = 4.5;
    /**
     * Within this a skeleton can't be missed: every arrow a player looses scatters by up to about a degree and a half
     * (the game's own spread), 0.6 of a block at 24, and an arrow hits anything within 0.3 of a skeleton's 0.6-wide body.
     * Further off it closes in first.
     */
    public static final double SURE = 24.0;
    static final Set<String> SKELETONS = Set.of("skeleton", "stray", "bogged", "wither_skeleton");
    static final Set<String> NO_ARROWS = Set.of("enderman", "breeze");

    /** The index of the hostile to shoot, or -1. {@code guard}: on guard, anything near the post is fair game. */
    public static int pick(List<Hostile> hostiles, Self self, boolean inWater, boolean guard) {
        if (!self.hasBow() || self.arrows() <= 0 || inWater) return -1;
        for (Hostile h : hostiles) {
            if (h.distance() < CLOSE && !"creeper".equals(h.type()) && CombatBrain.threat(h) > 0) return -1;
        }
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < hostiles.size(); i++) {
            Hostile h = hostiles.get(i);
            if (!h.lineOfSight()) continue;
            MobPolicy p = MobPolicy.of(h.type());
            if (p.mustFlee() || NO_ARROWS.contains(p.id())) continue;
            if (p.onlyIfAngry() && !h.angry() && !h.attacking()) continue;
            boolean creeper = "creeper".equals(h.type());
            if (h.distance() < (creeper ? CREEPER_MIN : MIN) || h.distance() > (SKELETONS.contains(h.type()) ? SURE : MAX)) continue;
            boolean worth = h.attacking() || guard && h.fromAnchor() <= 24
                    || h.closingSpeed() > 0.05 && h.distance() <= 20 || creeper && h.distance() <= 16
                    || p.preferRanged() && h.drawingBow();
            if (!worth) continue;
            double score = CombatBrain.threat(h) + (p.preferRanged() ? 30 : 0) + (SKELETONS.contains(h.type()) ? 25 : 0)
                    + (h.fusing() ? 50 : 0) - h.distance() * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
