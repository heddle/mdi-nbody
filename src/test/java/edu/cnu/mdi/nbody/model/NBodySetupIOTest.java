package edu.cnu.mdi.nbody.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NBodySetupIOTest {
    @TempDir Path temporary;

    @Test void roundTripsVersionedJson() throws Exception {
        Parameters parameters=new Parameters(2,.003,.0004,Parameters.Integrator.EULER);
        NBodySetup original=new NBodySetup(List.of(new Body(3,4,5,6,7,8),new Body(9,10,11,12,13,14)),parameters);
        Path file=temporary.resolve("model.json");
        NBodySetupIO.write(file,original);
        NBodySetup restored=NBodySetupIO.read(file);
        assertEquals(parameters,restored.parameters());
        assertEquals(original.bodies(),restored.bodies());
        assertTrue(java.nio.file.Files.readString(file).contains("\"version\" : 1"));
    }

    @Test void rejectsUnsupportedAndIncompleteDocuments() throws Exception {
        Path file=temporary.resolve("bad.json");
        java.nio.file.Files.writeString(file,"{\"version\":2,\"parameters\":null,\"bodies\":[]}");
        assertThrows(java.io.IOException.class,() -> NBodySetupIO.read(file));
        java.nio.file.Files.writeString(file,"{\"version\":1,\"parameters\":null,\"bodies\":[]}");
        assertThrows(java.io.IOException.class,() -> NBodySetupIO.read(file));
    }
}
