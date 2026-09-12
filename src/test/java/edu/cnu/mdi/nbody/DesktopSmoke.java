package edu.cnu.mdi.nbody;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import edu.cnu.mdi.desktop.Desktop;
import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.nbody.sim.NBodySimulation;
import edu.cnu.mdi.nbody.view.NBodyView;
import edu.cnu.mdi.sim.*;
import edu.cnu.mdi.util.PropertyUtils;

/** Explicit desktop smoke check; run with exec:java and the test classpath. Requires a display. */
public final class DesktopSmoke {
    public static void main(String[] args) throws Exception {
        AtomicReference<NBodyApp> app = new AtomicReference<>();
        AtomicReference<NBodyView> view = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                app.set(new NBodyApp(PropertyUtils.TITLE,"MDI N-body smoke test",PropertyUtils.FRACTION,.9));
                app.get().setVisible(true);
                for (JInternalFrame frame : Desktop.getInstance().getAllFrames()) if (frame instanceof NBodyView v) view.set(v);
            });
            NBodyView v=view.get();
            if (v == null) throw new AssertionError("Missing simulation view");
            await(() -> v.getSimulationState()==SimulationState.PAUSED);
            SwingUtilities.invokeAndWait(v::singleStep);
            await(() -> ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()==1 && v.getSimulationState()==SimulationState.PAUSED);
            if (((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()!=1) throw new AssertionError("Single step");
            SwingUtilities.invokeAndWait(v::runSimulation);
            await(() -> ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()>30);
            SimulationEngine old=v.getSimulationEngine();
            SwingUtilities.invokeAndWait(() -> v.reset(Presets.create(Presets.Preset.FIGURE_EIGHT,Parameters.defaults(),20,42),Parameters.defaults()));
            await(() -> v.getSimulationEngine()!=old && v.getSimulationState()==SimulationState.PAUSED);
            if (!old.awaitTermination(2000)) throw new AssertionError("Old engine leaked");
            if (((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()!=0) throw new AssertionError("Reset");
            SwingUtilities.invokeAndWait(() -> { v.setDisplay(true,true,true,100); v.runSimulation(); });
            Thread.sleep(1200);
            SwingUtilities.invokeAndWait(v::pauseSimulation);
            await(() -> v.getSimulationState()==SimulationState.PAUSED);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var image = new java.awt.image.BufferedImage(app.get().getWidth(),app.get().getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                    Graphics2D g=image.createGraphics(); app.get().paint(g); g.dispose();
                    ImageIO.write(image,"png",Path.of("target/desktop-smoke.png").toFile());
                } catch (Exception ex) { throw new RuntimeException(ex); }
            });
            SwingUtilities.invokeAndWait(() -> {
                // Closing with a reset pending must not create a replacement worker.
                v.reset(Presets.create(Presets.Preset.CIRCULAR,Parameters.defaults(),20,42),Parameters.defaults());
                v.prepareForExit();
            });
            if (!v.getSimulationEngine().awaitTermination(2000)) throw new AssertionError("Engine did not stop");
            System.out.println("Desktop smoke passed: views, single step, run/pause, reset, rendering, shutdown.");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (JInternalFrame frame : Desktop.getInstance().getAllFrames()) {
                    if (frame instanceof edu.cnu.mdi.view.BaseView base) base.prepareForExit();
                    frame.dispose();
                }
                for (Window window : Window.getWindows()) window.dispose();
            });
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(10);
        if (!condition.getAsBoolean()) throw new AssertionError("Timed out waiting for engine");
    }
}
