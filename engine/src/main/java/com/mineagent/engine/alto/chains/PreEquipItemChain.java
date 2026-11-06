// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/chains/PreEquipItemChain.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.chains;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.entity.AbstractKillEntityTask;
import com.mineagent.engine.alto.tasksystem.TaskChain;
import com.mineagent.engine.alto.tasksystem.TaskRunner;
import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.pathing.calc.IPath;
import com.mineagent.sbe.automatone.api.pathing.movement.IMovement;
import com.mineagent.sbe.automatone.pathing.movement.Movement;
import com.mineagent.sbe.automatone.utils.BlockStateInterface;

import java.util.Optional;

public class PreEquipItemChain extends SingleTaskChain {


    public PreEquipItemChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {

    }

    @Override
    public float getPriority() {
        update(AltoClef.getInstance());

        // we don't care about overtaking... just pre-equip items in the background
        return -1;
    }

    private void update(AltoClef mod) {
        if (mod.getFoodChain().isTryingToEat()) return;

        TaskChain currentChain = mod.getTaskRunner().getCurrentTaskChain();
        if (currentChain == null) return;

        // we will need to place or break some blocks, do not pre-equip anything...
        Optional<IPath> pathOptional = mod.getClientBaritone().getPathingBehavior().getPath();
        if (pathOptional.isEmpty()) return;

        IPath path = pathOptional.get();

        // should this really be created each tick?
        BlockStateInterface bsi = new BlockStateInterface(com.mineagent.engine.alto.AltoClef.getInstance().getClientBaritone().getPlayerContext());
        for (IMovement iMovement : path.movements()) {
            Movement movement = (Movement) iMovement;
            if (movement.toBreak(bsi).stream().anyMatch(pos -> mod.getWorld().getBlockState(pos).getBlock().defaultDestroyTime() > 0)
                    || !movement.toPlace(bsi).isEmpty()) return;
        }

        // we are *probably* trying to kill sth, might as well equip sword
        if (currentChain.getTasks().stream().anyMatch(task -> task instanceof AbstractKillEntityTask)) {
            AbstractKillEntityTask.equipWeapon(mod);
        }

    }

    @Override
    public String getName() {
        return "pre-equip item chain";
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
