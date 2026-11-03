package com.mineagent.engine.act;

/**
 * When a player may sprint, by the game's own rules (1.21.1, {@code LocalPlayer#canStartSprinting} and
 * {@code hasEnoughFoodToStartSprinting}): more than 6 food (or able to fly), not blind, not sneaking, not using an
 * item (eating, drawing a bow, holding up a shield). The server takes a fake player's word for sprinting, so the
 * companion holds itself to these (SMH plan 70, MA5.10). Pure.
 */
public final class SprintRule {
    /** Food at or below this and a player can't sprint. */
    public static final int MIN_FOOD = 6;

    private SprintRule() {}

    public static boolean allowed(int food, boolean mayFly, boolean blind, boolean sneaking, boolean usingItem) {
        return (food > MIN_FOOD || mayFly) && !blind && !sneaking && !usingItem;
    }
}
