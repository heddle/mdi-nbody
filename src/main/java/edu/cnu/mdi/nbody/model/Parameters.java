package edu.cnu.mdi.nbody.model;

/**
 * Numerical and physical parameters that remain fixed for one simulation run.
 * Changing any component creates a new initial-value problem rather than
 * mutating a model that is already running.
 *
 * @param g gravitational constant in dimensionless units
 * @param epsilon positive Plummer-softening length
 * @param dt fixed integration timestep
 * @param integrator numerical integration algorithm
 */
public record Parameters(double g, double epsilon, double dt, Integrator integrator) {

    /** Available fixed-step integration algorithms. */
    public enum Integrator {
        /** Time-reversible, symplectic velocity-Verlet integration. */
        VELOCITY_VERLET,

        /** First-order explicit Euler integration, included for comparison. */
        EULER
    }

    /**
     * Returns the general-purpose parameters used when the application opens.
     *
     * @return default parameters
     */
    public static Parameters defaults() {
        return new Parameters(1, 0.01, 0.002, Integrator.VELOCITY_VERLET);
    }

    /** Validates all parameters before the record is constructed. */
    public Parameters {
        if (!(g > 0) || !Double.isFinite(g) || !(epsilon > 0) || !Double.isFinite(epsilon)
                || !(dt > 0) || !Double.isFinite(dt) || integrator == null) {
            throw new IllegalArgumentException("G, softening, and timestep must be positive and finite");
        }
    }
}
