package com.mineagent.engine.planning.end;

import java.util.List;
import java.util.Optional;

/**
 * Finding the stronghold from eyes of ender (SMH plan 70, MA11.5; AltoClef's eye-throw method as an idea): each eye
 * thrown flies toward the stronghold; two eyes from places far enough apart, each a line on the ground from where it
 * was thrown along where the eye flew, cross near it. More eyes: the point nearest all the lines (least squares). Pure.
 */
public final class EyeTriangulation {
    private EyeTriangulation() {}

    /** One throw: where it was thrown from, and the flight's direction on the ground (any length). */
    public record Throw(double x, double z, double dx, double dz) {}

    /** Throws closer to parallel than this (degrees between them) don't fix a point. */
    public static final double MIN_ANGLE = 5.0;

    /** Where the lines of two or more eyes meet, or empty when they are too close to parallel. */
    public static Optional<double[]> locate(List<Throw> eyes) {
        if (eyes == null || eyes.size() < 2) return Optional.empty();
        // Least squares: the point minimising the squared distances to every line. For a line through p with unit
        // direction d, the distance-squared is |(I - d d^T)(q - p)|^2; summing gives A q = b.
        double a11 = 0, a12 = 0, a22 = 0, b1 = 0, b2 = 0;
        for (int i = 0; i < eyes.size(); i++) {
            Throw t = eyes.get(i);
            double len = Math.hypot(t.dx(), t.dz());
            if (len < 1e-9) continue;
            double ux = t.dx() / len, uz = t.dz() / len;
            double m11 = 1 - ux * ux, m12 = -ux * uz, m22 = 1 - uz * uz;
            a11 += m11;
            a12 += m12;
            a22 += m22;
            b1 += m11 * t.x() + m12 * t.z();
            b2 += m12 * t.x() + m22 * t.z();
        }
        double det = a11 * a22 - a12 * a12;
        if (Math.abs(det) < 1e-9 || Math.toDegrees(Math.asin(Math.min(1.0, maxCross(eyes)))) < MIN_ANGLE) return Optional.empty();
        double qx = (a22 * b1 - a12 * b2) / det;
        double qz = (a11 * b2 - a12 * b1) / det;
        return Optional.of(new double[] {qx, qz});
    }

    /** The widest angle between any two eyes, as the sine. */
    private static double maxCross(List<Throw> eyes) {
        double best = 0;
        for (int i = 0; i < eyes.size(); i++) {
            Throw a = eyes.get(i);
            double al = Math.hypot(a.dx(), a.dz());
            if (al < 1e-9) continue;
            for (int j = 0; j < i; j++) {
                Throw b = eyes.get(j);
                double bl = Math.hypot(b.dx(), b.dz());
                if (bl < 1e-9) continue;
                best = Math.max(best, Math.abs((a.dx() / al) * (b.dz() / bl) - (a.dz() / al) * (b.dx() / bl)));
            }
        }
        return best;
    }

    /**
     * Where to throw the second eye from: {@code apart} blocks to the side of the first throw's line, so the two lines
     * cross at a good angle.
     */
    public static double[] secondThrowFrom(Throw first, double apart) {
        double len = Math.hypot(first.dx(), first.dz());
        if (len < 1e-9) return new double[] {first.x() + apart, first.z()};
        return new double[] {first.x() - first.dz() / len * apart, first.z() + first.dx() / len * apart};
    }
}
