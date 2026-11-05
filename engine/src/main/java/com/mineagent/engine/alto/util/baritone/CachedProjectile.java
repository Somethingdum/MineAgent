// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/baritone/CachedProjectile.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.baritone;

import com.mineagent.engine.alto.util.time.TimerGame;
import java.lang.reflect.Type;
import net.minecraft.world.phys.Vec3;

public class CachedProjectile {
    private final TimerGame lastCache = new TimerGame(2);
    public Vec3 velocity;
    public Vec3 position;
    public double gravity;
    public Type projectileType;
    private Vec3 cachedHit;
    private boolean cacheHeld = false;

    public Vec3 getCachedHit() {
        return cachedHit;
    }

    public void setCacheHit(Vec3 cache) {
        cachedHit = cache;
        cacheHeld = true;
        lastCache.reset();
    }

    public boolean needsToRecache() {
        return !cacheHeld || lastCache.elapsed();
    }
}
