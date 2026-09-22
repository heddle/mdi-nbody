package edu.cnu.mdi.nbody.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Mutable numerical model for softened Newtonian gravity in two dimensions.
 *
 * <p>The simulation worker is the sole owner of this class. User-interface code
 * sees only immutable {@link Snapshot snapshots}; consequently this model has
 * no Swing or MDI dependencies. Pair forces are evaluated directly in
 * {@code O(N^2)} time.</p>
 */
public final class NBodyModel {

    private final Parameters parameters;
    private final List<Body> initial;
    private final double[] x;
    private final double[] y;
    private final double[] vx;
    private final double[] vy;
    private final double[] mass;
    private long step;

    /**
     * Creates a model from validated initial conditions.
     *
     * @param initial initial body states; must contain 2 through 50 unique IDs
     * @param parameters fixed parameters for the run
     * @throws IllegalArgumentException if the body count or IDs are invalid
     */
    public NBodyModel(List<Body> initial, Parameters parameters) {
        this.initial = List.copyOf(initial);
        this.parameters = Objects.requireNonNull(parameters, "parameters");

        int count = initial.size();
        if (count < 2 || count > 50) {
            throw new IllegalArgumentException("Use 2–50 bodies");
        }

        var ids = new HashSet<Integer>();
        for (Body body : initial) {
            if (!ids.add(body.id())) {
                throw new IllegalArgumentException("Duplicate body ID");
            }
        }

        x = new double[count];
        y = new double[count];
        vx = new double[count];
        vy = new double[count];
        mass = new double[count];
        reset();
    }

    /** Restores the constructor-supplied initial conditions and step zero. */
    public void reset() {
        step = 0;
        for (int i = 0; i < x.length; i++) {
            Body body = initial.get(i);
            x[i] = body.x();
            y[i] = body.y();
            vx[i] = body.vx();
            vy[i] = body.vy();
            mass[i] = body.mass();
        }
    }

    /**
     * Calculates every body's acceleration at the current positions.
     * Each pair is visited once, and the two acceleration updates come from one
     * equal-and-opposite force, preserving momentum to roundoff.
     *
     * @return array indexed as {@code acceleration[body][x-or-y]}
     */
    public double[][] accelerations() {
        double[][] acceleration = new double[x.length][2];
        double epsilonSquared = parameters.epsilon() * parameters.epsilon();

        for (int i = 0; i < x.length; i++) {
            for (int j = i + 1; j < x.length; j++) {
                double dx = x[j] - x[i];
                double dy = y[j] - y[i];
                double radiusSquared = dx * dx + dy * dy + epsilonSquared;
                double scale = parameters.g() / (radiusSquared * Math.sqrt(radiusSquared));

                acceleration[i][0] += mass[j] * scale * dx;
                acceleration[i][1] += mass[j] * scale * dy;
                acceleration[j][0] -= mass[i] * scale * dx;
                acceleration[j][1] -= mass[i] * scale * dy;
            }
        }
        return acceleration;
    }

    /** Advances the model by one fixed timestep using the selected integrator. */
    public void step() {
        double timestep = parameters.dt();
        double[][] acceleration = accelerations();
        boolean velocityVerlet = parameters.integrator() == Parameters.Integrator.VELOCITY_VERLET;

        // Velocity-Verlet applies its first half-kick before the shared drift.
        // Euler instead applies a full kick after drifting with the old velocity.
        for (int i = 0; i < x.length; i++) {
            if (velocityVerlet) {
                vx[i] += 0.5 * timestep * acceleration[i][0];
                vy[i] += 0.5 * timestep * acceleration[i][1];
            }
            x[i] += timestep * vx[i];
            y[i] += timestep * vy[i];
            if (!velocityVerlet) {
                vx[i] += timestep * acceleration[i][0];
                vy[i] += timestep * acceleration[i][1];
            }
        }

        if (velocityVerlet) {
            acceleration = accelerations();
            for (int i = 0; i < x.length; i++) {
                vx[i] += 0.5 * timestep * acceleration[i][0];
                vy[i] += 0.5 * timestep * acceleration[i][1];
            }
        }
        step++;
    }

    /**
     * Publishes an immutable view of the current state and its diagnostics.
     *
     * @return state at the current completed step
     */
    public Snapshot snapshot() {
        var bodies = new ArrayList<Body>(x.length);
        double kinetic = 0;
        double potential = 0;
        double momentumX = 0;
        double momentumY = 0;
        double angularMomentum = 0;
        double totalMass = 0;
        double weightedX = 0;
        double weightedY = 0;
        double epsilonSquared = parameters.epsilon() * parameters.epsilon();

        for (int i = 0; i < x.length; i++) {
            Body body = new Body(initial.get(i).id(), mass[i], x[i], y[i], vx[i], vy[i]);
            bodies.add(body);
            kinetic += body.kineticEnergy();
            totalMass += mass[i];
            momentumX += mass[i] * vx[i];
            momentumY += mass[i] * vy[i];
            angularMomentum += mass[i] * (x[i] * vy[i] - y[i] * vx[i]);
            weightedX += mass[i] * x[i];
            weightedY += mass[i] * y[i];

            for (int j = i + 1; j < x.length; j++) {
                double dx = x[j] - x[i];
                double dy = y[j] - y[i];
                // Use the same Plummer softening in the potential and force.
                potential -= parameters.g() * mass[i] * mass[j]
                        / Math.sqrt(dx * dx + dy * dy + epsilonSquared);
            }
        }

        Snapshot.Diagnostics diagnostics = new Snapshot.Diagnostics(
                kinetic,
                potential,
                momentumX,
                momentumY,
                angularMomentum,
                totalMass,
                weightedX / totalMass,
                weightedY / totalMass);
        return new Snapshot(step, step * parameters.dt(), bodies, diagnostics);
    }
}
