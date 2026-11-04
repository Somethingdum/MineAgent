package adris.altoclef.util.baritone;

import adris.altoclef.util.time.TimerGame;
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
