// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/IBaritoneProvider.java
// at commit 11ea66c (branch 1.21.1). SMH: one Baritone per companion, made by the engine with its body; no commands.
package com.mineagent.engine.baritone.api;

import com.mineagent.engine.baritone.api.cache.IWorldScanner;
import com.mineagent.engine.baritone.api.schematic.ISchematicSystem;

import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Provides the present {@link IBaritone} instances, as well as non-baritone instance related APIs.
 *
 * @author leijurv
 */
public interface IBaritoneProvider {

    /**
     * Returns the primary {@link IBaritone} instance. SMH: the companion whose Baritone is ticking.
     */
    IBaritone getPrimaryBaritone();

    /**
     * Returns all of the active {@link IBaritone} instances.
     */
    List<IBaritone> getAllBaritones();

    /**
     * Provides the {@link IBaritone} instance for a given player.
     */
    default IBaritone getBaritoneForPlayer(Entity player) {
        for (IBaritone baritone : this.getAllBaritones()) {
            var p = baritone.getPlayerContext().player();
            if (p == player || (p != null && player != null && p.getUUID().equals(player.getUUID()))) {
                return baritone;
            }
        }
        return null;
    }

    /**
     * Destroys a specified Baritone instance.
     */
    boolean destroyBaritone(IBaritone baritone);

    /**
     * Returns the {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();
}
