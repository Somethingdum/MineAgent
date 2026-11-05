// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/IBaritone.java
// at commit 11ea66c (branch 1.21.1). SMH: the parts AltoClef's tasks use, typed as the companion's Baritone (alto/baritone/Baritone).
package com.mineagent.engine.alto.baritone.api;

import com.mineagent.engine.alto.baritone.Baritone;
import com.mineagent.engine.alto.baritone.api.utils.IPlayerContext;

public interface IBaritone {

    Baritone.PathingBehavior getPathingBehavior();

    Baritone.CustomGoalProcess getCustomGoalProcess();

    Baritone.ExploreProcess getExploreProcess();

    Baritone.BuilderProcess getBuilderProcess();

    Baritone.InputOverrideHandler getInputOverrideHandler();

    Baritone.LookBehavior getLookBehavior();

    IPlayerContext getPlayerContext();
}
