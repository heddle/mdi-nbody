package edu.cnu.mdi.nbody.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Editable initial conditions for a future simulation run.
 *
 * <p>Unlike {@link NBodyModel}, a setup may be temporarily incomplete while a
 * user adds or removes bodies. Bodies are keyed by stable ID and retain their
 * insertion order for predictable display and serialization.</p>
 */
public final class NBodySetup {

    /** Minimum number of bodies accepted by the numerical model. */
    public static final int MIN_BODIES = 2;

    /** Maximum number of bodies accepted by the numerical model. */
    public static final int MAX_BODIES = 50;

    private final LinkedHashMap<Integer, Body> bodies = new LinkedHashMap<>();
    private Parameters parameters;

    /**
     * Creates an editable setup.
     *
     * @param bodies initial collection, which may be empty or incomplete
     * @param parameters parameters to use for the next run
     */
    public NBodySetup(Collection<Body> bodies, Parameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(bodies, "bodies").forEach(this::put);
    }

    /**
     * Creates a setup with no bodies.
     *
     * @param parameters parameters to use for the next run
     * @return empty editable setup
     */
    public static NBodySetup empty(Parameters parameters) {
        return new NBodySetup(List.of(), parameters);
    }

    /**
     * Returns an immutable snapshot of the bodies in insertion order.
     *
     * @return current body definitions
     */
    public List<Body> bodies() {
        return List.copyOf(bodies.values());
    }

    /**
     * Returns the parameters prepared for the next run.
     *
     * @return current setup parameters
     */
    public Parameters parameters() {
        return parameters;
    }

    /**
     * Replaces the parameters prepared for the next run.
     *
     * @param parameters new parameters
     */
    public void setParameters(Parameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    /**
     * Returns the number of bodies currently in the setup.
     *
     * @return current body count
     */
    public int size() {
        return bodies.size();
    }

    /**
     * Reports whether the setup satisfies the numerical model's body-count rule.
     *
     * @return {@code true} when the setup contains 2 through 50 bodies
     */
    public boolean canRun() {
        return size() >= MIN_BODIES && size() <= MAX_BODIES;
    }

    /**
     * Chooses the first integer ID above all IDs currently in use.
     *
     * @return unused body ID
     */
    public int nextId() {
        return bodies.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    /**
     * Adds a body or replaces the body having the same ID.
     *
     * @param body new body state
     * @throws IllegalStateException if adding a new ID would exceed 50 bodies
     */
    public void put(Body body) {
        Objects.requireNonNull(body, "body");
        if (!bodies.containsKey(body.id()) && bodies.size() >= MAX_BODIES) {
            throw new IllegalStateException("Use at most 50 bodies");
        }
        bodies.put(body.id(), body);
    }

    /**
     * Removes a body if its ID is present.
     *
     * @param id body ID
     */
    public void remove(int id) {
        bodies.remove(id);
    }

    /** Removes all bodies while retaining the current parameters. */
    public void clear() {
        bodies.clear();
    }

    /** Translates all positions so the center of mass lies at the origin. */
    public void centerOnMass() {
        if (bodies.isEmpty()) {
            return;
        }

        double totalMass = 0;
        double weightedX = 0;
        double weightedY = 0;
        for (Body body : bodies.values()) {
            totalMass += body.mass();
            weightedX += body.mass() * body.x();
            weightedY += body.mass() * body.y();
        }
        transform(1, 1, -weightedX / totalMass, -weightedY / totalMass);
    }

    /** Subtracts the center-of-mass velocity so total momentum becomes zero. */
    public void removeNetMomentum() {
        if (bodies.isEmpty()) {
            return;
        }

        double totalMass = 0;
        double momentumX = 0;
        double momentumY = 0;
        for (Body body : bodies.values()) {
            totalMass += body.mass();
            momentumX += body.mass() * body.vx();
            momentumY += body.mass() * body.vy();
        }

        double centerVelocityX = momentumX / totalMass;
        double centerVelocityY = momentumY / totalMass;
        for (Body body : List.copyOf(bodies.values())) {
            put(new Body(body.id(), body.mass(), body.x(), body.y(),
                    body.vx() - centerVelocityX, body.vy() - centerVelocityY));
        }
    }

    /** Reverses every velocity vector without changing positions or masses. */
    public void reverseVelocities() {
        for (Body body : List.copyOf(bodies.values())) {
            put(new Body(body.id(), body.mass(), body.x(), body.y(), -body.vx(), -body.vy()));
        }
    }

    /**
     * Scales every position about the origin.
     *
     * @param factor positive finite scale factor
     */
    public void scalePositions(double factor) {
        if (!(factor > 0) || !Double.isFinite(factor)) {
            throw new IllegalArgumentException("Scale must be positive and finite");
        }
        transform(factor, 1, 0, 0);
    }

    /**
     * Scales every velocity vector.
     *
     * @param factor finite scale factor; negative values reverse direction
     */
    public void scaleVelocities(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("Scale must be finite");
        }
        transform(1, factor, 0, 0);
    }

    /**
     * Copies a body, assigns a new ID, and offsets its position slightly.
     *
     * @param id ID of the body to copy
     * @return newly inserted body
     * @throws NullPointerException if the ID is unknown
     */
    public Body duplicate(int id) {
        Body body = Objects.requireNonNull(bodies.get(id), "Unknown body");
        Body copy = new Body(nextId(), body.mass(), body.x() + 0.1, body.y() + 0.1,
                body.vx(), body.vy());
        put(copy);
        return copy;
    }

    /**
     * Gives one body the prograde circular-orbit velocity for a fixed primary.
     * The calculation includes the primary's translational velocity but treats
     * it as dynamically fixed when estimating the circular speed.
     *
     * @param satelliteId body whose velocity is replaced
     * @param primaryId body supplying the central mass
     */
    public void setCircularVelocity(int satelliteId, int primaryId) {
        if (satelliteId == primaryId) {
            throw new IllegalArgumentException("Select two different bodies");
        }
        Body satellite = Objects.requireNonNull(bodies.get(satelliteId), "Unknown satellite");
        Body primary = Objects.requireNonNull(bodies.get(primaryId), "Unknown primary");
        double dx = satellite.x() - primary.x();
        double dy = satellite.y() - primary.y();
        double radius = Math.hypot(dx, dy);
        if (!(radius > 0)) {
            throw new IllegalArgumentException("Bodies must not coincide");
        }

        double speed = Math.sqrt(parameters.g() * primary.mass() / radius);
        put(new Body(satellite.id(), satellite.mass(), satellite.x(), satellite.y(),
                primary.vx() - speed * dy / radius,
                primary.vy() + speed * dx / radius));
    }

    private void transform(double positionScale, double velocityScale, double dx, double dy) {
        for (Body body : List.copyOf(bodies.values())) {
            put(new Body(body.id(), body.mass(),
                    body.x() * positionScale + dx,
                    body.y() * positionScale + dy,
                    body.vx() * velocityScale,
                    body.vy() * velocityScale));
        }
    }
}
