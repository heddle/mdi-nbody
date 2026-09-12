package edu.cnu.mdi.nbody.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Presets {
    private Presets() { }
    public enum Preset {
        CIRCULAR("Two-body circular"), ECCENTRIC("Two-body eccentric"), FIGURE_EIGHT("Three-body figure-eight"),
        BINARY("Binary + low-mass body"), CLUSTER("Random cluster");
        private final String label;
        Preset(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public static List<Body> create(Preset preset, Parameters p, int count, long seed) {
        double scale = Math.sqrt(p.g());
        if (preset == Preset.FIGURE_EIGHT) {
            double vx = 0.466203685 * scale, vy = 0.432365730 * scale;
            // Equal-mass Newtonian choreography; positive softening perturbs the exact orbit.
            return List.of(new Body(0, 1, -0.97000436, 0.24308753, vx, vy),
                    new Body(1, 1, 0.97000436, -0.24308753, vx, vy), new Body(2, 1, 0, 0, -2*vx, -2*vy));
        }
        if (preset == Preset.CLUSTER) {
            if (count < 2 || count > 50) throw new IllegalArgumentException("Use 2–50 bodies");
            Random random = new Random(seed);
            var bodies = new ArrayList<Body>();
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * 2 * Math.PI, r = 3 * Math.sqrt(random.nextDouble());
                bodies.add(new Body(i, 0.5 + random.nextDouble(), r*Math.cos(angle), r*Math.sin(angle),
                        scale * 0.2 * random.nextGaussian(), scale * 0.2 * random.nextGaussian()));
            }
            return centered(bodies);
        }
        // Separation is 2, masses are 1: softened circular speed follows the pair acceleration.
        double v = Math.sqrt(p.g() * 2 / Math.pow(4 + p.epsilon()*p.epsilon(), 1.5));
        if (preset == Preset.ECCENTRIC) v *= 0.55;
        var bodies = new ArrayList<>(List.of(new Body(0, 1, -1, 0, 0, -v), new Body(1, 1, 1, 0, 0, v)));
        if (preset == Preset.BINARY) bodies.add(new Body(2, 0.01, 4, 0, 0, Math.sqrt(2*p.g()/4)));
        return centered(bodies);
    }
    private static List<Body> centered(List<Body> bodies) {
        double m = 0, x = 0, y = 0, vx = 0, vy = 0;
        for (Body b : bodies) { m += b.mass(); x += b.mass()*b.x(); y += b.mass()*b.y(); vx += b.mass()*b.vx(); vy += b.mass()*b.vy(); }
        var result = new ArrayList<Body>();
        for (Body b : bodies) result.add(new Body(b.id(), b.mass(), b.x()-x/m, b.y()-y/m, b.vx()-vx/m, b.vy()-vy/m));
        return List.copyOf(result);
    }
}
