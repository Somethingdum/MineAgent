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
            updateState();
            dirty = false;
        }
    }

    protected abstract void updateState();

    protected abstract void reset();
}
