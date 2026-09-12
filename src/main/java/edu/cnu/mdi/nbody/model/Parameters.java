package edu.cnu.mdi.nbody.model;

/** Fixed for the duration of a run; changing physics starts a new initial-value problem. */
public record Parameters(double g, double epsilon, double dt, Integrator integrator) {
    public enum Integrator { VELOCITY_VERLET, EULER }
    public static Parameters defaults() { return new Parameters(1, 0.01, 0.002, Integrator.VELOCITY_VERLET); }
    public Parameters {
        if (!(g > 0) || !Double.isFinite(g) || !(epsilon > 0) || !Double.isFinite(epsilon)
                || !(dt > 0) || !Double.isFinite(dt) || integrator == null) {
            throw new IllegalArgumentException("G, softening, and timestep must be positive and finite");
        }
    }
}
