package edu.cnu.mdi.nbody.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads and writes versioned JSON documents containing editable N-body setups. */
public final class NBodySetupIO {
    private static final int FORMAT_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NBodySetupIO() { }

    /**
     * Writes a setup as human-readable, versioned JSON.
     *
     * @param path destination file
     * @param setup setup to persist
     * @throws IOException if the file cannot be written
     */
    public static void write(Path path, NBodySetup setup) throws IOException {
        Document document = new Document(FORMAT_VERSION, setup.parameters(), setup.bodies());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), document);
    }

    /**
     * Reads and validates a setup document.
     *
     * @param path source JSON file
     * @return editable setup represented by the document
     * @throws IOException if the file cannot be read, has an unsupported version,
     *         or does not describe a valid setup
     */
    public static NBodySetup read(Path path) throws IOException {
        Document document = MAPPER.readValue(path.toFile(), Document.class);
        if (document == null) {
            throw new IOException("Empty N-body model");
        }
        if (document.version() != FORMAT_VERSION) {
            throw new IOException("Unsupported model version: " + document.version());
        }
        try {
            if (document.bodies() == null || document.parameters() == null) {
                throw new IllegalArgumentException("Missing bodies or parameters");
            }
            return new NBodySetup(document.bodies(),document.parameters());
        } catch (RuntimeException ex) {
            throw new IOException("Invalid N-body model", ex);
        }
    }

    /**
     * On-disk representation of a saved setup.
     *
     * @param version file-format version
     * @param parameters physical and numerical parameters
     * @param bodies saved initial body states
     */
    public record Document(int version, Parameters parameters, List<Body> bodies) { }
}
