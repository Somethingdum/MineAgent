package com.mineagent.engine.settings;

import java.util.Locale;

/**
 * Who decides what the companion works on (the Andy Omega plan, §59; SMH plan 69). Every mode keeps the
 * reflexes, survival, the owner's stop and verified execution; none can switch them off.
 *
 * <ul>
 *   <li>{@code servant} (the default): your goals; it does what they need and starts no project of its own.
 *       Idle, it stays useful and out of the way.</li>
 *   <li>{@code auto}: it chooses its own work and practises (the curriculum), and yields at once to an order.</li>
 *   <li>{@code escort}: it stays with you and keeps you alive; guarding only, no wandering.</li>
 *   <li>{@code parked}: nothing but reflexes; it holds its place and only talks.</li>
 * </ul>
 */
public enum WorkMode {
    SERVANT, AUTO, ESCORT, PARKED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkMode of(String id) {
        String k = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "auto" -> AUTO;
            case "escort" -> ESCORT;
            case "parked", "park" -> PARKED;
            default -> SERVANT;
        };
    }
}
