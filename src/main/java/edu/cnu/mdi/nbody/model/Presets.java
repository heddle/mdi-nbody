package edu.cnu.mdi.nbody.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Factory for the application's named initial-condition sets. */
public final class Presets {

    private Presets() { }

    /** Presets offered by the simulation control panel. */
    public enum Preset {
        /** Equal-mass softened circular binary. */
        CIRCULAR("Two-body circular"),

        /** Equal-mass binary launched below circular speed. */
        ECCENTRIC("Two-body eccentric"),

        /** Equal-mass three-body figure-eight choreography. */
        FIGURE_EIGHT("Three-body figure-eight"),

        /** Three massive stars and a dynamically negligible planet. */
        THREE_SUNS("Chaotic three suns + planet"),

        /** Sun, Mercury, and Jupiter model used by the perihelion diagnostic. */
        MERCURY_JUPITER("Sun, Mercury + Jupiter"),

        /** Equal-mass binary with a distant low-mass third body. */
        BINARY("Binary + low-mass body"),

        /** Seeded random disk of point masses. */
        CLUSTER("Random cluster"),

        /** Marker for initial conditions edited by the user. */
        CUSTOM("Custom model");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Returns the small softening and timestep recommended for the
     * Sun-Mercury-Jupiter preset.
     *
     * @return recommended perihelion-experiment parameters
     */
    public static Parameters mercuryPrecessionParameters() {
        return new Parameters(1, 0.0001, 0.0002, Parameters.Integrator.VELOCITY_VERLET);
    }

    /**
     * Creates body states for a named preset.
     *
     * @param preset preset to create
     * @param parameters physical parameters used to scale preset velocities
     * @param count body count for {@link Preset#CLUSTER}; ignored otherwise
     * @param seed random seed for {@link Preset#CLUSTER}; ignored otherwise
     * @return immutable list of initial body states
     */
    public static List<Body> create(Preset preset, Parameters parameters, int count, long seed) {
        if (preset == Preset.CUSTOM) {
            throw new IllegalArgumentException("Custom models use the bodies on the canvas");
        }

        double velocityScale = Math.sqrt(parameters.g());
        return switch (preset) {
            case FIGURE_EIGHT -> figureEight(velocityScale);
            case CLUSTER -> randomCluster(count, seed, velocityScale);
            case THREE_SUNS -> threeSuns(parameters);
            case MERCURY_JUPITER -> mercuryAndJupiter(parameters);
            case CIRCULAR, ECCENTRIC, BINARY -> binaryFamily(preset, parameters);
            case CUSTOM -> throw new AssertionError("Handled above");
        };
    }

    private static List<Body> figureEight(double velocityScale) {
        double vx = 0.466203685 * velocityScale;
        double vy = 0.432365730 * velocityScale;
        // These are the standard unsoftened choreography coordinates. Positive
        // softening intentionally perturbs the exact periodic solution.
        return List.of(
                new Body(0, 1, -0.97000436, 0.24308753, vx, vy),
                new Body(1, 1, 0.97000436, -0.24308753, vx, vy),
                new Body(2, 1, 0, 0, -2 * vx, -2 * vy));
    }

    private static List<Body> randomCluster(int count, long seed, double velocityScale) {
        if (count < NBodySetup.MIN_BODIES || count > NBodySetup.MAX_BODIES) {
            throw new IllegalArgumentException("Use 2–50 bodies");
        }

        Random random = new Random(seed);
        var bodies = new ArrayList<Body>(count);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = 3 * Math.sqrt(random.nextDouble());
            bodies.add(new Body(i,
                    0.5 + random.nextDouble(),
                    radius * Math.cos(angle),
                    radius * Math.sin(angle),
                    velocityScale * 0.2 * random.nextGaussian(),
                    velocityScale * 0.2 * random.nextGaussian()));
        }
        return centered(bodies);
    }

    private static List<Body> threeSuns(Parameters parameters) {
        // This deliberately asymmetric, non-periodic stellar system is an
        // educational interpretation, not canonical initial data from the novel.
        // The fourth body's mass is small enough to be dynamically negligible.
        return centered(List.of(
                new Body(0, 3, 1, 3, 0, 0),
                new Body(1, 4, -2, -1, 0, 0),
                new Body(2, 5, 1, -1, 0, 0),
                new Body(3, 0.001, 1.25, 3, 0, Math.sqrt(12 * parameters.g()))));
    }

    private static List<Body> mercuryAndJupiter(Parameters parameters) {
        double mercuryMass = 1.660e-7;
        double jupiterMass = 9.545e-4;
        double mercurySemimajorAxis = 0.3871;
        double mercuryEccentricity = 0.2056;
        double mercuryPerihelion = mercurySemimajorAxis * (1 - mercuryEccentricity);
        double mercurySpeed = Math.sqrt(parameters.g() * (1 + mercuryMass)
                * (1 + mercuryEccentricity) / mercuryPerihelion);
        double jupiterRadius = 5.203;
        double jupiterSpeed = Math.sqrt(parameters.g() * (1 + jupiterMass) / jupiterRadius);

        return centered(List.of(
                new Body(0, 1, 0, 0, 0, 0),
                new Body(1, mercuryMass, mercuryPerihelion, 0, 0, mercurySpeed),
                new Body(2, jupiterMass, jupiterRadius, 0, 0, jupiterSpeed)));
    }

    private static List<Body> binaryFamily(Preset preset, Parameters parameters) {
        // Separation is 2 and each primary has unit mass. The softened circular
        // speed follows directly from the pair acceleration at radius 2.
        double epsilonSquared = parameters.epsilon() * parameters.epsilon();
        double speed = Math.sqrt(parameters.g() * 2 / Math.pow(4 + epsilonSquared, 1.5));
        if (preset == Preset.ECCENTRIC) {
            speed *= 0.55;
        }

        var bodies = new ArrayList<>(List.of(
                new Body(0, 1, -1, 0, 0, -speed),
                new Body(1, 1, 1, 0, 0, speed)));
        if (preset == Preset.BINARY) {
            bodies.add(new Body(2, 0.01, 4, 0, 0, Math.sqrt(2 * parameters.g() / 4)));
        }
        return centered(bodies);
    }

    /** Returns a copy translated to zero center-of-mass position and velocity. */
    private static List<Body> centered(List<Body> bodies) {
        double totalMass = 0;
        double weightedX = 0;
        double weightedY = 0;
        double momentumX = 0;
        double momentumY = 0;
        for (Body body : bodies) {
            totalMass += body.mass();
            weightedX += body.mass() * body.x();
            weightedY += body.mass() * body.y();
            momentumX += body.mass() * body.vx();
            momentumY += body.mass() * body.vy();
        }

        var result = new ArrayList<Body>(bodies.size());
        for (Body body : bodies) {
            result.add(new Body(body.id(), body.mass(),
                    body.x() - weightedX / totalMass,
                    body.y() - weightedY / totalMass,
                    body.vx() - momentumX / totalMass,
                    body.vy() - momentumY / totalMass));
        }
        return List.copyOf(result);
    }
}
