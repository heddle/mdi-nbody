package edu.cnu.mdi.nbody.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON persistence for editable model definitions. */
public final class NBodySetupIO {
    private static final ObjectMapper MAPPER=new ObjectMapper();
    private NBodySetupIO() { }
    public static void write(Path path,NBodySetup setup) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),new Document(1,setup.parameters(),setup.bodies()));
    }
    public static NBodySetup read(Path path) throws IOException {
        Document document=MAPPER.readValue(path.toFile(),Document.class);
        if (document==null) throw new IOException("Empty N-body model");
        if (document.version()!=1) throw new IOException("Unsupported model version: " + document.version());
        try {
            if (document.bodies()==null || document.parameters()==null) throw new IllegalArgumentException("Missing bodies or parameters");
            return new NBodySetup(document.bodies(),document.parameters());
        }
        catch (RuntimeException ex) { throw new IOException("Invalid N-body model",ex); }
    }
    public record Document(int version,Parameters parameters,List<Body> bodies) { }
}
