package edu.cnu.mdi.nbody.model;

/** Immutable body state in dimensionless simulation units. */
public record Body(int id, double mass, double x, double y, double vx, double vy) {
    public Body {
        if (!(mass > 0) || !Double.isFinite(mass) || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(vx) || !Double.isFinite(vy)) {
            throw new IllegalArgumentException("Body requires positive finite mass and finite coordinates");
        }
    }
    public double speed() { return Math.hypot(vx, vy); }
    public double kineticEnergy() { return 0.5 * mass * (vx * vx + vy * vy); }
}
