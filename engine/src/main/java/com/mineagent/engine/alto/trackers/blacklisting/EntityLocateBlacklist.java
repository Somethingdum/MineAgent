// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/trackers/blacklisting/EntityLocateBlacklist.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.trackers.blacklisting;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EntityLocateBlacklist extends AbstractObjectBlacklist<Entity> {
    @Override
    protected Vec3 getPos(Entity item) {
        return item.position();
    }
}
