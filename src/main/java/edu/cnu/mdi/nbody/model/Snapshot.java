package edu.cnu.mdi.nbody.model;

import java.util.List;

/**
 * Immutable state published by the simulation worker for display on the EDT.
 * The constructor defensively copies the body list, making a snapshot a safe
 * publication boundary between the mutable numerical model and all consumers.
 *
 * @param step number of completed integration steps
 * @param time simulation time, equal to {@code step * dt}
 * @param bodies immutable body states in model order
 * @param diagnostics conserved quantities and aggregate properties
 */
public record Snapshot(long step, double time, List<Body> bodies, Diagnostics diagnostics) {

    /** Defensively copies the collection component. */
    public Snapshot {
        bodies = List.copyOf(bodies);
    }

    /**
     * Aggregate quantities computed from the same state as the body list.
     *
     * @param kinetic total kinetic energy
     * @param potential total softened gravitational potential energy
     * @param px x-component of total linear momentum
     * @param py y-component of total linear momentum
     * @param angularMomentum total angular momentum about the origin
     * @param mass total system mass
     * @param comX x-coordinate of the center of mass
     * @param comY y-coordinate of the center of mass
     */
    public record Diagnostics(double kinetic, double potential, double px, double py,
                              double angularMomentum, double mass, double comX, double comY) {

        /**
         * Returns the total mechanical energy.
         *
         * @return kinetic plus potential energy
         */
        public double energy() {
            return kinetic + potential;
        }
    }
}
