// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasksystem/ITaskUsesCraftingGrid.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasksystem;

/**
 * Some tasks (mainly tasks that open containers) will break if they don't require
 * that the 2x2 crafting grid is empty. Tasks which implement this interface
 * require implementers to maintain an empty crafting grid.
 * <p>
 * This interface let's a task declare that it MUST have certain slots clear before it can execute.
 */
public interface ITaskUsesCraftingGrid {

}
