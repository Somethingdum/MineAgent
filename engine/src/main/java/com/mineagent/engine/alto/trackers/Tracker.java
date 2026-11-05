// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/trackers/Tracker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.trackers;

import com.mineagent.engine.alto.AltoClef;

public abstract class Tracker {

    protected AltoClef mod;
    // Needs to update
    private boolean dirty = true;

    public Tracker(TrackerManager manager) {
        manager.addTracker(this);
    }

    public void setDirty() {
        dirty = true;
    }

    // Virtual
    protected boolean isDirty() {
        return dirty;
    }

    protected void ensureUpdated() {
        if (isDirty()) {
            // SMH: brought up to date on the server's own thread only. Baritone's path threads ask too (AltoClef's
            // run-away and dodge goals ask for the hostiles and projectiles in their heuristics); an update there walks
            // the server's entity lists off its thread, holding the lock the server thread then waits on, and two
            // benchmark worlds froze. Off it, the state last made on the server thread is read, under the same lock.
            if (!onServerThread()) return;
            updateState();
            dirty = false;
        }
    }

    /** SMH: whether this is the server's own thread (its companion's server), not one of Baritone's. */
    private boolean onServerThread() {
        var player = mod == null ? null : mod.getPlayer();
        var server = player == null ? null : player.getServer();
        if (server != null) return server.isSameThread();
        return !Thread.currentThread().getName().startsWith("MineAgent Baritone");
    }

    protected abstract void updateState();

    protected abstract void reset();
}
