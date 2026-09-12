package edu.cnu.mdi.nbody.sim;

import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.sim.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.function.BooleanSupplier;

class NBodySimulationTest {
    @Test void engineSingleStepPauseResumeStopAndFreshReset() throws Exception {
        Parameters p = Parameters.defaults();
        var initial = Presets.create(Presets.Preset.CIRCULAR,p,20,42);
        var sim = new NBodySimulation(initial,p);
        var engine = new SimulationEngine(sim,new SimulationEngineConfig(0,0,0,true));
        sim.bind(engine);
        try {
            engine.start(); await(() -> engine.getState()==SimulationState.PAUSED);
            var snapshot = sim.snapshot();
            sim.requestSingleStep(); await(() -> sim.snapshot().step()==1 && engine.getState()==SimulationState.PAUSED);
            assertEquals(1,sim.snapshot().step()); assertEquals(p.dt(),sim.snapshot().time());
            Thread.sleep(40); assertEquals(1,sim.snapshot().step());
            sim.requestSingleStep(); await(() -> sim.snapshot().step()==2 && engine.getState()==SimulationState.PAUSED);
            engine.requestRun(); await(() -> sim.snapshot().step()>=8);
            engine.requestPause(); await(() -> engine.getState()==SimulationState.PAUSED);
            long paused = sim.snapshot().step(); Thread.sleep(30); assertEquals(paused,sim.snapshot().step());
            engine.requestStop(); assertTrue(engine.awaitTermination(2000));
            assertEquals(snapshot,new NBodySimulation(initial,p).snapshot());
        } finally { engine.requestStop(); assertTrue(engine.awaitTermination(2000)); }
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime()+2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(),"Engine did not reach expected state");
    }
}
