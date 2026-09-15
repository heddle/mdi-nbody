package edu.cnu.mdi.nbody;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import edu.cnu.mdi.desktop.Desktop;
import edu.cnu.mdi.item.ItemChangeType;
import edu.cnu.mdi.graphics.toolbar.BaseToolBar;
import edu.cnu.mdi.graphics.toolbar.ToolBits;
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
            if (!(v.getToolBar() instanceof BaseToolBar toolbar)
                    || !toolbar.hasTool(ToolBits.getId(ToolBits.CAMERA))) throw new AssertionError("Missing camera tool");
            await(() -> v.getSimulationState()==SimulationState.PAUSED);
            SimulationEngine setupEngine=v.getSimulationEngine();
            SwingUtilities.invokeAndWait(v::newModel);
            if (!v.isEditingSetup() || v.getDefaultLayer().size()!=0) throw new AssertionError("New model");
            SwingUtilities.invokeAndWait(() -> {
                Component canvas=v.getIContainer().getComponent();
                for (Point point:new Point[]{new Point(canvas.getWidth()/3,canvas.getHeight()/2),
                        new Point(2*canvas.getWidth()/3,canvas.getHeight()/2),new Point(canvas.getWidth()/2,canvas.getHeight()/3)}) {
                    v.addBody();
                    canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,
                            point.x,point.y,1,false,MouseEvent.BUTTON1));
                }
            });
            if (v.getSimulationEngine()!=setupEngine || v.getDefaultLayer().size()!=3 || !v.canRunSetup())
                throw new AssertionError("Click-to-place setup");
            if (v.getDefaultLayer().getAllItems().stream().anyMatch(item -> !item.isDraggable()))
                throw new AssertionError("Paused bodies should be editable");
            SwingUtilities.invokeAndWait(() -> {
                var item=v.getDefaultLayer().getAllItems().get(2);
                item.setFocus(new Point2D.Double(2.5,1.5));
                v.getDefaultLayer().notifyItemChangeListeners(item,ItemChangeType.MOVED);
            });
            if (v.getDefaultLayer().getAllItems().get(2).getFocus().x!=2.5) throw new AssertionError("Move body");
            SwingUtilities.invokeAndWait(() -> v.getDefaultLayer().deleteItem(v.getDefaultLayer().getAllItems().get(2)));
            if (v.getDefaultLayer().size()!=2) throw new AssertionError("Delete body");
            SwingUtilities.invokeAndWait(v::startOrRun);
            await(() -> v.getSimulationEngine()!=setupEngine
                    && ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()>30);
            if (v.getDefaultLayer().getAllItems().stream().anyMatch(item -> item.isDraggable()))
                throw new AssertionError("Running bodies should be locked");
            SwingUtilities.invokeAndWait(v::stopSimulation);
            await(() -> v.getSimulationState()==SimulationState.TERMINATED
                    && v.getDefaultLayer().getAllItems().stream().allMatch(item -> item.isDraggable()));
            SimulationEngine old=v.getSimulationEngine();
            SwingUtilities.invokeAndWait(v::restoreCurrentModel);
            await(() -> v.getSimulationEngine()!=old && v.getSimulationState()==SimulationState.PAUSED);
            if (!old.awaitTermination(2000)) throw new AssertionError("Old engine leaked");
            Snapshot restored=((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot();
            if (restored.step()!=0 || restored.bodies().size()!=2) throw new AssertionError("Reset current model");
            SimulationEngine beforePhysics=v.getSimulationEngine();
            SwingUtilities.invokeAndWait(() -> components(v,JSpinner.class).stream()
                    .filter(spinner -> Math.abs(((Number)spinner.getValue()).doubleValue()-.002)<1e-15)
                    .findFirst().orElseThrow().setValue(.00002));
            if (!v.isEditingSetup() || v.getSimulationEngine()!=beforePhysics)
                throw new AssertionError("Physics change should prepare, not start, a custom model");
            SwingUtilities.invokeAndWait(v::startOrRun);
            await(() -> v.getSimulationEngine()!=beforePhysics
                    && Math.abs(((NBodySimulation)v.getSimulationEngine().getSimulation()).parameters().dt()-.00002)<1e-15);
            SwingUtilities.invokeAndWait(v::stopSimulation);
            await(() -> v.getSimulationState()==SimulationState.TERMINATED);
            SimulationEngine beforeLoad=v.getSimulationEngine();
            SwingUtilities.invokeAndWait(() -> v.loadPreset(Presets.Preset.FIGURE_EIGHT,
                    Presets.create(Presets.Preset.FIGURE_EIGHT,Parameters.defaults(),20,42),Parameters.defaults()));
            if (v.getSimulationEngine()!=beforeLoad || !v.isEditingSetup() || v.getDefaultLayer().size()!=3)
                throw new AssertionError("Load Preset should only prepare a model");
            SwingUtilities.invokeAndWait(v::startOrRun);
            await(() -> v.getSimulationEngine()!=beforeLoad
                    && ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()>1);
            SwingUtilities.invokeAndWait(v::pauseSimulation);
            await(() -> v.getSimulationState()==SimulationState.PAUSED);
            for (int resetIndex=0;resetIndex<4;resetIndex++) {
                SimulationEngine resetEngine=v.getSimulationEngine();
                SwingUtilities.invokeAndWait(v::restoreCurrentModel);
                await(() -> v.getSimulationEngine()!=resetEngine
                        && ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()==0
                        && v.getSimulationState()==SimulationState.PAUSED
                        && v.getDefaultLayer().getAllItems().stream().allMatch(item -> item.isDraggable()
                        && item.isSelectable() && item.isDoubleClickable()));
                for (var item:v.getDefaultLayer().getAllItems()) if (!item.isDraggable()
                        || !item.isSelectable() || !item.isDoubleClickable())
                    throw new AssertionError("Bodies lost interaction after Reset " + resetIndex
                            + ": draggable=" + item.isDraggable() + " selectable=" + item.isSelectable()
                            + " doubleClickable=" + item.isDoubleClickable() + " state=" + v.getSimulationState());
            }
            SwingUtilities.invokeAndWait(v::singleStep);
            await(() -> ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()==1
                    && v.getSimulationState()==SimulationState.PAUSED);
            Parameters precession=Presets.mercuryPrecessionParameters();
            SimulationEngine beforePrecession=v.getSimulationEngine();
            SwingUtilities.invokeAndWait(() -> v.loadPreset(Presets.Preset.MERCURY_JUPITER,
                    Presets.create(Presets.Preset.MERCURY_JUPITER,precession,20,42),precession));
            if (v.getSimulationEngine()!=beforePrecession) throw new AssertionError("Preset load started a run");
            SwingUtilities.invokeAndWait(v::startOrRun);
            await(() -> ((NBodySimulation)v.getSimulationEngine().getSimulation()).snapshot().step()>10
                    && java.util.Arrays.stream(Window.getWindows()).anyMatch(window -> window.isVisible()
                    && window instanceof Dialog dialog && "Mercury perihelion precession".equals(dialog.getTitle())));
            SwingUtilities.invokeAndWait(v::invalidateSpecialExperiment);
            await(() -> java.util.Arrays.stream(Window.getWindows()).noneMatch(window -> window.isVisible()
                    && window instanceof Dialog dialog && "Mercury perihelion precession".equals(dialog.getTitle())));
            SwingUtilities.invokeAndWait(() -> { v.setDisplay(true,true,true,100); v.runSimulation(); });
            Thread.sleep(1200);
            SwingUtilities.invokeAndWait(v::pauseSimulation);
            await(() -> v.getSimulationState()==SimulationState.PAUSED);
            await(() -> v.getDefaultLayer().getAllItems().stream().allMatch(item -> item.isDraggable()));
            SwingUtilities.invokeAndWait(() -> {
                var item=v.getDefaultLayer().getAllItems().get(2);
                Point point=new Point(); v.getIContainer().worldToLocal(point,item.getFocus());
                if (v.getIContainer().getItemAtPoint(point)!=item) throw new AssertionError("Body hit testing");
                Component canvas=v.getIContainer().getComponent();
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,
                        point.x,point.y,1,false,MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,
                        point.x,point.y,1,false,MouseEvent.BUTTON1));
                if (!item.isSelected()) throw new AssertionError("Pointer selection after pause");
            });
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
    private static <T extends Component> java.util.List<T> components(Container root,Class<T> type) {
        var result=new java.util.ArrayList<T>();
        for (Component component:root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(components(child,type));
        }
        return result;
    }
}
