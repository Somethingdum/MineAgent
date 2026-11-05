// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/time/BaseTimer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.time;

public abstract class BaseTimer {
    private double prevTime = 0;
    private double interval;

    public BaseTimer(double intervalSeconds) {
        interval = intervalSeconds;
    }

    public double getDuration() {
        return currentTime() - prevTime;
    }

    public void setInterval(double interval) {
        this.interval = interval;
    }

    public boolean elapsed() {
        return getDuration() > interval;
    }

    public void reset() {
        prevTime = currentTime();
    }

    public void forceElapse() {
        prevTime = 0;
    }

    protected abstract double currentTime();

    protected void setPrevTimeForce(double toSet) {
        prevTime = toSet;
    }

    protected double getPrevTime() {
        return prevTime;
    }

}
