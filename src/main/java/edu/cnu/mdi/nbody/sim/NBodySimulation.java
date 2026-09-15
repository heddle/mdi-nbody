package edu.cnu.mdi.nbody.sim;

import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.sim.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** MDI adapter: only the engine thread advances the model; consumers see immutable snapshots. */
public final class NBodySimulation implements Simulation {
    private final NBodyModel model;
    private final Parameters parameters;
    private final AtomicBoolean singleStep = new AtomicBoolean();
    private volatile Snapshot latest;
    private volatile int updateHz = 30;
    private SimulationEngine engine;
    private long lastRefresh;

    public NBodySimulation(List<Body> initial, Parameters parameters) {
        model = new NBodyModel(initial, parameters);
        this.parameters = parameters;
        latest = model.snapshot();
    }
    /** Bind before starting the engine. */
    public void bind(SimulationEngine engine) { this.engine = java.util.Objects.requireNonNull(engine); }
    public Snapshot snapshot() { return latest; }
    public Parameters parameters() { return parameters; }
    public void setUpdateHz(int hz) {
        if (hz < 1 || hz > 60) throw new IllegalArgumentException("Update rate must be 1–60 Hz");
        updateHz = hz;
    }
    /** Call only while READY/PAUSED. The engine pauses itself after exactly one numerical step. */
    public void requestSingleStep() { singleStep.set(true); engine.requestRun(); }
    @Override public void init(SimulationContext ctx) {
        // Use autoRun=true plus an initial pause: the engine acknowledges PAUSED before
        // controls can resume it. This avoids a READY/requestRun race in MDI 1.2.3.
        engine.requestPause();
        engine.requestRefresh();
    }
    @Override public boolean step(SimulationContext ctx) {
        long start = System.nanoTime();
        model.step();
        latest = model.snapshot();
        if (singleStep.getAndSet(false)) {
            engine.requestPause();
            engine.requestRefresh();
        } else {
            if (start - lastRefresh >= 1_000_000_000L / updateHz) {
                lastRefresh = start;
                engine.requestRefresh();
            }
            // Pace the existing worker, without introducing another scheduler. No variable physics step.
            long delay = Math.min(20_000_000L, (long)(parameters.dt()*1e9)) - (System.nanoTime()-start);
            if (delay > 0) LockSupport.parkNanos(delay);
        }
        return true;
    }
    @Override public void shutdown(SimulationContext ctx) { engine.requestRefresh(); }
}
