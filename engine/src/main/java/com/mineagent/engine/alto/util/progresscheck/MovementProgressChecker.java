// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/progresscheck/MovementProgressChecker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.progresscheck;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class MovementProgressChecker {

    private final IProgressChecker<Vec3> distanceChecker;
    private final IProgressChecker<Double> mineChecker;

    private BlockPos lastBreakingBlock = null;

    public MovementProgressChecker(double distanceTimeout, double minDistance, double mineTimeout, double minMineProgress, int attempts) {
        distanceChecker = new ProgressCheckerRetry<>(new DistanceProgressChecker(distanceTimeout, minDistance), attempts);
        mineChecker = new LinearProgressChecker(mineTimeout, minMineProgress);
    }

    public MovementProgressChecker(double distanceTimeout, double minDistance, double mineTimeout, double minMineProgress) {
        this(distanceTimeout, minDistance, mineTimeout, minMineProgress, 1);
    }

    public MovementProgressChecker(int attempts) {
        this(6, 0.1, 0.5, 0.001, attempts);
    }

    public MovementProgressChecker() {
        this(1);
    }

    public boolean check(AltoClef mod) {

        // Allow pause on eat
        if (mod.getFoodChain().needsToEat()) {
            distanceChecker.reset();
            mineChecker.reset();
        }

        if (mod.getControllerExtras().isBreakingBlock()) {
            BlockPos breakBlock = mod.getControllerExtras().getBreakingBlockPos();
            // If we broke a block, we made progress.
            // We must also delay reseting the distance checker UNTIL we break a block.
            // Because otherwise we risk not failing if we keep retrtying to mine and don't succeed.
            if (lastBreakingBlock != null && WorldHelper.isAir(lastBreakingBlock)) {
                distanceChecker.reset();
                mineChecker.reset();
            }
            lastBreakingBlock = breakBlock;
            mineChecker.setProgress(mod.getControllerExtras().getBreakingBlockProgress());
            return !mineChecker.failed();
        } else {
            mineChecker.reset();
            distanceChecker.setProgress(mod.getPlayer().position());
            return !distanceChecker.failed();
        }
    }

    public void reset() {
        distanceChecker.reset();
        mineChecker.reset();
    }

}
