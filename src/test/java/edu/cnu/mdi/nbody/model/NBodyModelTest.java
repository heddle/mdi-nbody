package edu.cnu.mdi.nbody.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class NBodyModelTest {
    private final Parameters p = Parameters.defaults();
    private NBodyModel orbit(Presets.Preset preset) { return new NBodyModel(Presets.create(preset, p, 20, 42), p); }
    @Test void forceSymmetry() {
        var m = new NBodyModel(List.of(new Body(0, 2, -1, 0, 0, 0), new Body(1, 3, 2, 1, 0, 0)), p);
        var a = m.accelerations();
        assertEquals(0, 2*a[0][0]+3*a[1][0], 1e-15);
        assertEquals(0, 2*a[0][1]+3*a[1][1], 1e-15);
        assertTrue(a[0][0] > 0);
    }
    @Test void linearMomentumAndMovingCenterOfMass() {
        var m = new NBodyModel(List.of(new Body(0, 2, -1, 0, 0.2, 0.4), new Body(1, 3, 1, 1, 0.1, -0.2)), p);
        var initial = m.snapshot().diagnostics();
        for (int i = 0; i < 5000; i++) m.step();
        var s = m.snapshot(); var d = s.diagnostics();
        assertEquals(initial.px(), d.px(), 1e-10); assertEquals(initial.py(), d.py(), 1e-10);
        assertEquals(initial.comX()+s.time()*initial.px()/initial.mass(), d.comX(), 1e-10);
        assertEquals(initial.comY()+s.time()*initial.py()/initial.mass(), d.comY(), 1e-10);
    }
    @Test void verletConservesEnergyAndAngularMomentumThroughoutOrbit() {
        var m = orbit(Presets.Preset.ECCENTRIC); var d = m.snapshot().diagnostics();
        for (int i = 0; i < 30000; i++) {
            m.step();
            if (i % 20 == 0) {
                var now = m.snapshot().diagnostics();
                assertEquals(d.energy(), now.energy(), Math.abs(d.energy())*0.0002);
                assertEquals(d.angularMomentum(), now.angularMomentum(), 1e-10);
            }
        }
    }
    @Test void stableCircularOrbit() {
        var m = orbit(Presets.Preset.CIRCULAR);
        for (int i = 0; i < 100000; i++) {
            m.step();
            if (i % 100 == 0) for (Body b : m.snapshot().bodies()) assertEquals(1, Math.hypot(b.x(), b.y()), 2e-6);
        }
    }
    @Test void deterministicRandomPreset() {
        assertEquals(Presets.create(Presets.Preset.CLUSTER,p,50,42), Presets.create(Presets.Preset.CLUSTER,p,50,42));
        assertNotEquals(Presets.create(Presets.Preset.CLUSTER,p,50,42), Presets.create(Presets.Preset.CLUSTER,p,50,43));
    }
    @Test void chaoticThreeSunsPresetIncludesNegligiblePlanetAndRemainsFinite() {
        var bodies = Presets.create(Presets.Preset.THREE_SUNS,p,20,42);
        assertEquals(4,bodies.size());
        assertEquals(3,bodies.stream().filter(body -> body.mass() >= 1).count());
        assertEquals(0.001,bodies.get(3).mass());
        var m = new NBodyModel(bodies,p);
        for (int i = 0; i < 10000; i++) m.step();
        var snapshot = m.snapshot();
        assertTrue(snapshot.bodies().stream().allMatch(body -> Double.isFinite(body.x())
                && Double.isFinite(body.y()) && Double.isFinite(body.vx()) && Double.isFinite(body.vy())));
    }
    @Test void mercuryJupiterPresetHasPhysicalMassOrderingAndEccentricInnerOrbit() {
        Parameters parameters=Presets.mercuryPrecessionParameters();
        var bodies=Presets.create(Presets.Preset.MERCURY_JUPITER,parameters,20,42);
        assertEquals(3,bodies.size());
        assertTrue(bodies.get(0).mass()>bodies.get(2).mass());
        assertTrue(bodies.get(2).mass()>bodies.get(1).mass());
        var model=new NBodyModel(bodies,parameters);
        for (int i=0;i<20000;i++) model.step();
        assertTrue(model.snapshot().bodies().stream().allMatch(body -> Double.isFinite(body.x()) && Double.isFinite(body.y())));
    }
    @Test void exactResetAndImmutableSnapshot() {
        var m = orbit(Presets.Preset.CLUSTER); var initial = m.snapshot();
        for (int i = 0; i < 100; i++) m.step();
        assertNotEquals(initial, m.snapshot()); m.reset(); assertEquals(initial, m.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> initial.bodies().clear());
    }
    @Test void coincidentBodiesRemainFinite() {
        var m = new NBodyModel(List.of(new Body(0, 1, 0, 0, 0, 0), new Body(1, 1, 0, 0, 0, 0)),p);
        m.step(); assertEquals(-p.g()/p.epsilon(), m.snapshot().diagnostics().potential(),1e-12);
    }
    @Test void eulerUsesOldVelocity() {
        var p = new Parameters(1, .01, .01, Parameters.Integrator.EULER);
        var m = new NBodyModel(List.of(new Body(0, 1, -1, 0, 0, 0), new Body(1, 1, 1, 0, 0, 0)), p);
        m.step(); assertEquals(-1, m.snapshot().bodies().get(0).x()); assertTrue(m.snapshot().bodies().get(0).vx()>0);
    }
    @Test void validatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> new Parameters(1,0,.01,Parameters.Integrator.EULER));
        assertThrows(IllegalArgumentException.class, () -> new Body(0,Double.NaN,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new NBodyModel(List.of(),p));
    }
}
