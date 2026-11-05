// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/helpers/MathsHelper.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.helpers;

import net.minecraft.world.phys.Vec3;

/**
 * Math utilities
 * <p>
 * I'm not British. I swear. I just don't want it to conflict with minecraft's `MathHelper`.
 */
public interface MathsHelper {

    static Vec3 project(Vec3 vec, Vec3 onto, boolean assumeOntoNormalized) {
        if (!assumeOntoNormalized) {
            onto = onto.normalize();
        }
        return onto.scale(vec.dot(onto));
    }

    static Vec3 project(Vec3 vec, Vec3 onto) {
        return project(vec, onto, false);
    }

    static Vec3 projectOntoPlane(Vec3 vec, Vec3 normal, boolean assumeNormalNormalized) {
        Vec3 p = project(vec, normal, assumeNormalNormalized);
        return vec.subtract(p);
    }

    static Vec3 projectOntoPlane(Vec3 vec, Vec3 normal) {
        return projectOntoPlane(vec, normal, false);
    }
}
