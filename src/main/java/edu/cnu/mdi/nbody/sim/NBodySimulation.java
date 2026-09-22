package edu.cnu.mdi.nbody.sim;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.nbody.model.NBodyModel;
import edu.cnu.mdi.nbody.model.Parameters;
import edu.cnu.mdi.nbody.model.Snapshot;
import edu.cnu.mdi.sim.Simulation;
import edu.cnu.mdi.sim.SimulationContext;
import edu.cnu.mdi.sim.SimulationEngine;

/**
 * Adapts the framework-independent {@link NBodyModel} to MDI's simulation
 * engine.
 *
 * <p>Only the engine worker advances the model. The most recent immutable
 * {@link Snapshot} is held in a volatile field so EDT consumers always observe
 * a complete state. Refresh requests are throttled independently of the fixed
 * physics timestep.</p>
 */
public final class NBodySimulation implements Simulation {

    private static final long MAX_PACING_DELAY_NANOS = 20_000_000L;

    private final NBodyModel model;
    private final Parameters parameters;
    private final AtomicBoolean singleStep = new AtomicBoolean();
    private volatile Snapshot latest;
    private volatile int updateHz = 30;
    private SimulationEngine engine;
    private long lastRefresh;

    /**
     * Creates the simulation adapter and its worker-owned numerical model.
     *
     * @param initial initial body states
     * @param parameters fixed parameters for this run
     */
    public NBodySimulation(List<Body> initial, Parameters parameters) {
        model = new NBodyModel(initial, parameters);
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        latest = model.snapshot();
    }

    /**
     * Associates this simulation with its engine before the engine starts.
     *
     * @param engine owning simulation engine
     */
    public void bind(SimulationEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Returns the most recently published immutable state.
     *
     * @return latest complete snapshot
     */
    public Snapshot snapshot() {
        return latest;
    }

    /**
     * Returns the fixed parameters used by this run.
     *
     * @return run parameters
     */
    public Parameters parameters() {
        return parameters;
    }

    /**
     * Sets the maximum rate at which the worker requests display refreshes.
     *
     * @param hz refresh requests per second, from 1 through 60
     */
    public void setUpdateHz(int hz) {
        if (hz < 1 || hz > 60) {
            throw new IllegalArgumentException("Update rate must be 1–60 Hz");
        }
        updateHz = hz;
    }

    /**
     * Requests exactly one numerical step followed by another pause.
     * Call this only while the engine is ready or paused.
     */
    public void requestSingleStep() {
        singleStep.set(true);
        engine.requestRun();
    }

    @Override
    public void init(SimulationContext context) {
        // The replacement engine is auto-started, then immediately acknowledges
        // PAUSED. This avoids a READY/requestRun race in the framework lifecycle.
        engine.requestPause();
        engine.requestRefresh();
    }

    @Override
    public boolean step(SimulationContext context) {
        long started = System.nanoTime();
        model.step();
        latest = model.snapshot();

        if (singleStep.getAndSet(false)) {
            engine.requestPause();
            engine.requestRefresh();
        } else {
            requestPeriodicRefresh(started);
            paceWorker(started);
        }
        return true;
    }

    private void requestPeriodicRefresh(long now) {
        if (now - lastRefresh >= 1_000_000_000L / updateHz) {
            lastRefresh = now;
            engine.requestRefresh();
        }
    }

    private void paceWorker(long started) {
        // Pacing uses the existing worker; it does not introduce a scheduler or
        // alter the fixed physics step. Very large dt values are capped so Stop
        // and Pause remain responsive.
        long targetDelay = Math.min(MAX_PACING_DELAY_NANOS, (long) (parameters.dt() * 1e9));
        long remaining = targetDelay - (System.nanoTime() - started);
        if (remaining > 0) {
            LockSupport.parkNanos(remaining);
        }
    }

    @Override
    public void shutdown(SimulationContext context) {
        engine.requestRefresh();
    }
}
