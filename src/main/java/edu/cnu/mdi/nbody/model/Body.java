package edu.cnu.mdi.nbody.model;

/**
 * Describes one point mass at a particular instant.
 *
 * <p>All values use the application's dimensionless simulation units. A body is
 * immutable, so instances can safely cross the worker-thread/EDT boundary as
 * part of a {@link Snapshot}.</p>
 *
 * @param id stable identifier used to associate the body with its MDI item
 * @param mass positive point mass
 * @param x x-coordinate
 * @param y y-coordinate
 * @param vx x-component of velocity
 * @param vy y-component of velocity
 */
public record Body(int id, double mass, double x, double y, double vx, double vy) {

    /** Validates the physical state stored by this record. */
    public Body {
        if (!(mass > 0) || !Double.isFinite(mass) || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(vx) || !Double.isFinite(vy)) {
            throw new IllegalArgumentException("Body requires positive finite mass and finite coordinates");
        }
    }

    /**
     * Returns the magnitude of the velocity vector.
     *
     * @return {@code sqrt(vx^2 + vy^2)}
     */
    public double speed() {
        return Math.hypot(vx, vy);
    }

    /**
     * Returns this body's translational kinetic energy.
     *
     * @return {@code mass * speed^2 / 2}
     */
    public double kineticEnergy() {
        return 0.5 * mass * (vx * vx + vy * vy);
    }
}
