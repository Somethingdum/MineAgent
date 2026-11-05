// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/utils/SettingsUtil.java
// at commit 11ea66c (branch 1.21.1). SMH: only maybeCensor; coordinates are never hidden in the companion's own traces.
package com.mineagent.engine.alto.baritone.api.utils;

public final class SettingsUtil {

    private SettingsUtil() {
    }

    public static String maybeCensor(int coord) {
        return Integer.toString(coord);
    }
}
