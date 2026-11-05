// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/CachedProjectile.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.altoclef.util.time.TimerGame;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Type;

public class CachedProjectile {
   private final TimerGame lastCache = new TimerGame(2.0);
   public Vec3 velocity;
   public Vec3 position;
   public double gravity;
   public Type projectileType;
   private Vec3 cachedHit;
   private boolean cacheHeld = false;

   public Vec3 getCachedHit() {
      return this.cachedHit;
   }

   public void setCacheHit(Vec3 cache) {
      this.cachedHit = cache;
      this.cacheHeld = true;
      this.lastCache.reset();
   }

   public boolean needsToRecache() {
      return !this.cacheHeld || this.lastCache.elapsed();
   }
}
