package adris.altoclef.util.progresscheck;

import net.minecraft.world.phys.Vec3;

public class DistanceProgressChecker implements IProgressChecker<Vec3> {

    private final IProgressChecker<Double> distanceChecker;
    private final boolean reduceDistance;
    private Vec3 start;
    private Vec3 prevPos;

    public DistanceProgressChecker(IProgressChecker<Double> distanceChecker, boolean reduceDistance) {
        this.distanceChecker = distanceChecker;
        this.reduceDistance = reduceDistance;
        if (reduceDistance) {
            this.distanceChecker.setProgress(Double.NEGATIVE_INFINITY);
        }
        reset();
    }

    public DistanceProgressChecker(double timeout, double minDistanceToMake, boolean reduceDistance) {
        this(new LinearProgressChecker(timeout, minDistanceToMake), reduceDistance);
    }

    public DistanceProgressChecker(double timeout, double minDistanceToMake) {
        this(timeout, minDistanceToMake, false);
    }

    @Override
    public void setProgress(Vec3 position) {
        if (start == null) {
            start = position;
            return;
        }
        double delta = position.distanceTo(start);
        // If we want to reduce distance, penalize distance.
        if (reduceDistance) delta *= -1;
        prevPos = position;
        distanceChecker.setProgress(delta);
    }

    @Override
    public boolean failed() {
        return distanceChecker.failed();
    }

    @Override
    public void reset() {
        start = null;//_prevPos;
        distanceChecker.setProgress(0.0);
        distanceChecker.reset();
    }
}
