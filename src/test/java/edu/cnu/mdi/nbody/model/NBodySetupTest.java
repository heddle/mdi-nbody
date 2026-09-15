package edu.cnu.mdi.nbody.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NBodySetupTest {
    @Test void permitsIncompleteSetupButValidatesRunReadiness() {
        NBodySetup setup=NBodySetup.empty(Parameters.defaults());
        assertFalse(setup.canRun());
        setup.put(new Body(0,1,0,0,0,0)); assertFalse(setup.canRun());
        setup.put(new Body(1,1,1,0,0,0)); assertTrue(setup.canRun());
        setup.remove(0); assertFalse(setup.canRun());
    }
    @Test void replacesByIdAndReturnsImmutableSnapshot() {
        NBodySetup setup=NBodySetup.empty(Parameters.defaults());
        setup.put(new Body(7,1,0,0,0,0)); setup.put(new Body(7,2,3,4,5,6));
        assertEquals(1,setup.size()); assertEquals(2,setup.bodies().get(0).mass());
        assertThrows(UnsupportedOperationException.class,() -> setup.bodies().clear());
    }
    @Test void centersMassAndRemovesNetMomentum() {
        NBodySetup setup=new NBodySetup(java.util.List.of(
                new Body(0,2,-1,3,4,-2),new Body(1,1,5,-3,-1,5)),Parameters.defaults());
        setup.centerOnMass(); setup.removeNetMomentum();
        double mass=setup.bodies().stream().mapToDouble(Body::mass).sum();
        assertEquals(0,setup.bodies().stream().mapToDouble(b -> b.mass()*b.x()).sum()/mass,1e-14);
        assertEquals(0,setup.bodies().stream().mapToDouble(b -> b.mass()*b.y()).sum()/mass,1e-14);
        assertEquals(0,setup.bodies().stream().mapToDouble(b -> b.mass()*b.vx()).sum(),1e-14);
        assertEquals(0,setup.bodies().stream().mapToDouble(b -> b.mass()*b.vy()).sum(),1e-14);
    }
    @Test void duplicatesScalesReversesAndBuildsCircularVelocity() {
        NBodySetup setup=new NBodySetup(java.util.List.of(
                new Body(2,4,0,0,.5,-.25),new Body(7,1,2,0,0,0)),Parameters.defaults());
        Body duplicate=setup.duplicate(7);
        assertEquals(8,duplicate.id()); assertEquals(3,setup.size());
        setup.setCircularVelocity(7,2);
        Body satellite=setup.bodies().stream().filter(b -> b.id()==7).findFirst().orElseThrow();
        assertEquals(.5,satellite.vx(),1e-14);
        assertEquals(-.25+Math.sqrt(2),satellite.vy(),1e-14);
        setup.scalePositions(2); setup.scaleVelocities(.5); setup.reverseVelocities();
        satellite=setup.bodies().stream().filter(b -> b.id()==7).findFirst().orElseThrow();
        assertEquals(4,satellite.x(),1e-14); assertEquals(-.25,satellite.vx(),1e-14);
        assertThrows(IllegalArgumentException.class,() -> setup.scalePositions(0));
        assertThrows(IllegalArgumentException.class,() -> setup.setCircularVelocity(2,2));
    }
}
