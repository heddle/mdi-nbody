package edu.cnu.mdi.nbody.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Single-owner mutable numerical state. No UI or framework dependencies. O(N²) pair forces. */
public final class NBodyModel {
    private final Parameters parameters;
    private final List<Body> initial;
    private final double[] x, y, vx, vy, mass;
    private long step;

    public NBodyModel(List<Body> initial, Parameters parameters) {
        this.initial = List.copyOf(initial);
        this.parameters = java.util.Objects.requireNonNull(parameters);
        int n = initial.size();
        if (n < 2 || n > 50) throw new IllegalArgumentException("Use 2–50 bodies");
        var ids = new HashSet<Integer>();
        for (Body b : initial) if (!ids.add(b.id())) throw new IllegalArgumentException("Duplicate body ID");
        x = new double[n]; y = new double[n]; vx = new double[n]; vy = new double[n]; mass = new double[n];
        reset();
    }

    public void reset() {
        step = 0;
        for (int i = 0; i < x.length; i++) {
            Body b = initial.get(i);
            x[i] = b.x(); y[i] = b.y(); vx[i] = b.vx(); vy[i] = b.vy(); mass[i] = b.mass();
        }
    }

    /** Pair updates enforce equal and opposite forces, including unequal masses. */
    public double[][] accelerations() {
        double[][] a = new double[x.length][2];
        for (int i = 0; i < x.length; i++) for (int j = i + 1; j < x.length; j++) {
            double dx = x[j] - x[i], dy = y[j] - y[i];
            double r2 = dx * dx + dy * dy + parameters.epsilon() * parameters.epsilon();
            double f = parameters.g() / (r2 * Math.sqrt(r2));
            a[i][0] += mass[j] * f * dx; a[i][1] += mass[j] * f * dy;
            a[j][0] -= mass[i] * f * dx; a[j][1] -= mass[i] * f * dy;
        }
        return a;
    }

    public void step() {
        double h = parameters.dt();
        double[][] a = accelerations();
        boolean verlet = parameters.integrator() == Parameters.Integrator.VELOCITY_VERLET;
        for (int i = 0; i < x.length; i++) {
            if (verlet) { vx[i] += 0.5 * h * a[i][0]; vy[i] += 0.5 * h * a[i][1]; }
            x[i] += h * vx[i]; y[i] += h * vy[i];
            if (!verlet) { vx[i] += h * a[i][0]; vy[i] += h * a[i][1]; }
        }
        if (verlet) {
            a = accelerations();
            for (int i = 0; i < x.length; i++) { vx[i] += 0.5 * h * a[i][0]; vy[i] += 0.5 * h * a[i][1]; }
        }
        step++;
    }

    public Snapshot snapshot() {
        var bodies = new ArrayList<Body>(x.length);
        double k = 0, u = 0, px = 0, py = 0, l = 0, m = 0, cx = 0, cy = 0;
        for (int i = 0; i < x.length; i++) {
            Body b = new Body(initial.get(i).id(), mass[i], x[i], y[i], vx[i], vy[i]);
            bodies.add(b); k += b.kineticEnergy(); m += mass[i];
            px += mass[i] * vx[i]; py += mass[i] * vy[i];
            l += mass[i] * (x[i] * vy[i] - y[i] * vx[i]);
            cx += mass[i] * x[i]; cy += mass[i] * y[i];
            for (int j = i + 1; j < x.length; j++) {
                double dx = x[j] - x[i], dy = y[j] - y[i];
                // Potential and acceleration use the same Plummer softening.
                u -= parameters.g() * mass[i] * mass[j]
                        / Math.sqrt(dx * dx + dy * dy + parameters.epsilon() * parameters.epsilon());
            }
        }
        return new Snapshot(step, step * parameters.dt(), bodies,
                new Snapshot.Diagnostics(k, u, px, py, l, m, cx / m, cy / m));
    }
}
