// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md):
// adris/altoclef/chains/MobDefenseChain.java at commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: what
// AltoClef's tasks set and ask; the fighting is the companion's own mob defence (survival/MobDefenseChain, itself partly
// ported from this class), which reads the target and force field range a task sets here.
package com.mineagent.engine.alto.chains;

import net.minecraft.world.entity.Entity;

public class MobDefenseChain {

    private Entity targetEntity;
    private double forceFieldRange = Double.POSITIVE_INFINITY;
    private boolean shielding;
    private boolean doingFunkyStuff;
    private boolean puttingOutFire;

    public void setTargetEntity(Entity entity) {
        targetEntity = entity;
    }

    public void resetTargetEntity() {
        targetEntity = null;
    }

    public Entity getTargetEntity() {
        return targetEntity;
    }

    public void setForceFieldRange(double range) {
        forceFieldRange = range;
    }

    public void resetForceField() {
        forceFieldRange = Double.POSITIVE_INFINITY;
    }

    public double getForceFieldRange() {
        return forceFieldRange;
    }

    public boolean isDoingAcrobatics() {
        return doingFunkyStuff;
    }

    public boolean isPuttingOutFire() {
        return puttingOutFire;
    }

    public boolean isShielding() {
        return shielding;
    }

    /** SMH: what the companion's own mob defence is doing, set by the host each tick. */
    public void observe(boolean shielding, boolean acrobatics, boolean puttingOutFire) {
        this.shielding = shielding;
        this.doingFunkyStuff = acrobatics;
        this.puttingOutFire = puttingOutFire;
    }
}
