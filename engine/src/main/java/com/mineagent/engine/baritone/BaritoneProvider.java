// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/BaritoneProvider.java
// at commit 11ea66c (branch 1.21.1). SMH: the companions' Baritones; the one ticking now is the primary one.
package com.mineagent.engine.baritone;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.baritone.api.IBaritone;
import com.mineagent.engine.baritone.api.IBaritoneProvider;
import com.mineagent.engine.baritone.api.cache.IWorldScanner;
import com.mineagent.engine.baritone.api.schematic.ISchematicSystem;
import com.mineagent.engine.baritone.cache.WorldScanner;
import com.mineagent.engine.baritone.utils.schematic.SchematicSystem;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider {

    private final List<IBaritone> all = new CopyOnWriteArrayList<>();
    /** SMH: the Baritone ticking on the server thread now. */
    private static final ThreadLocal<Baritone> CURRENT = new ThreadLocal<>();

    @Override
    public IBaritone getPrimaryBaritone() {
        Baritone now = CURRENT.get();
        if (now != null) return now;
        return all.isEmpty() ? null : all.get(0);
    }

    @Override
    public List<IBaritone> getAllBaritones() {
        return Collections.unmodifiableList(all);
    }

    /** SMH: a companion's Baritone, made for its body. */
    public synchronized Baritone createBaritone(AgentPlayer agent, ServerPlayer player) {
        Baritone baritone = new Baritone(agent, player);
        all.add(baritone);
        return baritone;
    }

    @Override
    public boolean destroyBaritone(IBaritone baritone) {
        return baritone != null && all.remove(baritone);
    }

    /** SMH: the Baritone ticking (or searching) on this thread now, or null. */
    public static Baritone current() {
        return CURRENT.get();
    }

    /** SMH: this Baritone is the primary one while {@code run} runs (its tick, a task's call into it). */
    public static <T> T as(Baritone baritone, java.util.function.Supplier<T> run) {
        Baritone before = CURRENT.get();
        CURRENT.set(baritone);
        try {
            return run.get();
        } finally {
            CURRENT.set(before);
        }
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return WorldScanner.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
