package com.mineagent.engine.chat;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * Who a companion listens to, and how (SMH plan 69, MA1.7). The user's words, 2026-11-02: "I need the
 * bot to be able to speak to any player. While only obeying ME and anyone I whitelist."
 *
 * <ul>
 *   <li>{@code OWNER}: the companion's owner (their chat reaches the companion as before);</li>
 *   <li>{@code TRUSTED}: a player on the companion's trusted list, within 32 blocks: an order, like the owner's;</li>
 *   <li>{@code TALK}: anyone else who names the companion or stands within 8 blocks: talk only, with no
 *       tools, no task and no inbox entry, whatever they say;</li>
 *   <li>{@code IGNORE}: everyone else.</li>
 * </ul>
 */
public final class ChatRouting {
    public enum Route { OWNER, TRUSTED, TALK, IGNORE }

    /** A trusted player's order is heard this far away, at most. */
    public static final double TRUSTED_BLOCKS = 32.0;
    /** Anyone this close is talked to without naming the companion. */
    public static final double NEAR_BLOCKS = 8.0;

    private ChatRouting() {}

    /**
     * @param distanceBlocks how far the sender is from the companion, in blocks (another dimension:
     *                       {@link Double#POSITIVE_INFINITY})
     * @param mentionsName   whether the message names the companion
     */
    public static Route route(UUID sender, UUID owner, Collection<String> trusted, double distanceBlocks, boolean mentionsName) {
        if (sender == null) return Route.IGNORE;
        if (sender.equals(owner)) return Route.OWNER;
        boolean near = !Double.isNaN(distanceBlocks) && distanceBlocks <= NEAR_BLOCKS;
        if (trusted != null && trusted.contains(sender.toString().toLowerCase(Locale.ROOT))
                && !Double.isNaN(distanceBlocks) && distanceBlocks <= TRUSTED_BLOCKS) {
            return Route.TRUSTED;
        }
        return mentionsName || near ? Route.TALK : Route.IGNORE;
    }

    /** Whether a chat message names the companion (as a word, any case, with or without @). */
    public static boolean mentions(String message, String companionName) {
        if (message == null || companionName == null || companionName.isBlank()) return false;
        String m = message.toLowerCase(Locale.ROOT);
        String n = companionName.toLowerCase(Locale.ROOT).strip();
        // Chinese and other unspaced scripts have no word edges: a name in them counts anywhere.
        if (n.chars().anyMatch(c -> c > 127)) return m.contains(n);
        int at = m.indexOf(n);
        while (at >= 0) {
            boolean startOk = at == 0 || !Character.isLetterOrDigit(m.charAt(at - 1));
            int end = at + n.length();
            boolean endOk = end >= m.length() || !Character.isLetterOrDigit(m.charAt(end));
            if (startOk && endOk) return true;
            at = m.indexOf(n, at + 1);
        }
        return false;
    }
}
