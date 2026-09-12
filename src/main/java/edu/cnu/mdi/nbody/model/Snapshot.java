package edu.cnu.mdi.nbody.model;

import java.util.List;

/** Deeply immutable publication boundary between the model and any consumers. */
public record Snapshot(long step, double time, List<Body> bodies, Diagnostics diagnostics) {
    public Snapshot { bodies = List.copyOf(bodies); }
    public record Diagnostics(double kinetic, double potential, double px, double py,
                              double angularMomentum, double mass, double comX, double comY) {
        public double energy() { return kinetic + potential; }
    }
}
