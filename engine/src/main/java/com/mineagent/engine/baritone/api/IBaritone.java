// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/IBaritone.java
// at commit 11ea66c (branch 1.21.1). SMH: one per companion; no world cache, commands, elytra or click GUI.
package com.mineagent.engine.baritone.api;

import com.mineagent.engine.baritone.api.behavior.ILookBehavior;
import com.mineagent.engine.baritone.api.behavior.IPathingBehavior;
import com.mineagent.engine.baritone.api.event.listener.IEventBus;
import com.mineagent.engine.baritone.api.pathing.calc.IPathingControlManager;
import com.mineagent.engine.baritone.api.process.*;
import com.mineagent.engine.baritone.api.selection.ISelectionManager;
import com.mineagent.engine.baritone.api.utils.IInputOverrideHandler;
import com.mineagent.engine.baritone.api.utils.IPlayerContext;

/**
 * @author Brady
 * @since 9/29/2018
 */
public interface IBaritone {

    IPathingBehavior getPathingBehavior();

    ILookBehavior getLookBehavior();

    IFollowProcess getFollowProcess();

    IMineProcess getMineProcess();

    IBuilderProcess getBuilderProcess();

    IExploreProcess getExploreProcess();

    IFarmProcess getFarmProcess();

    ICustomGoalProcess getCustomGoalProcess();

    IGetToBlockProcess getGetToBlockProcess();

    IPathingControlManager getPathingControlManager();

    IInputOverrideHandler getInputOverrideHandler();

    IPlayerContext getPlayerContext();

    IEventBus getGameEventHandler();

    ISelectionManager getSelectionManager();
}
