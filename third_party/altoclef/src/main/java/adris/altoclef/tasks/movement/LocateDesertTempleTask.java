package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;

public class LocateDesertTempleTask extends Task {

    private BlockPos _finalPos;

    @Override
    protected void onStart() {
    }

    @Override
    protected Task onTick() {
        BlockPos desertTemplePos = WorldHelper.getADesertTemple();
        if (desertTemplePos != null) {
            _finalPos = desertTemplePos.above(14);
        }
        if (_finalPos != null) {
            setDebugState("Going to found desert temple");
            return new GetToBlockTask(_finalPos, false);
        }
        return new SearchWithinBiomeTask(Biomes.DESERT);
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LocateDesertTempleTask;
    }

    @Override
    protected String toDebugString() {
        return "Searchin' for temples";
    }

    @Override
    public boolean isFinished() {
        return AltoClef.getInstance().getPlayer().blockPosition().equals(_finalPos);
    }
}
