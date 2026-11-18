package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import edu.cnu.mdi.graphics.style.SymbolType;
import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.nbody.model.Parameters;
import edu.cnu.mdi.nbody.model.Snapshot;
import edu.cnu.mdi.splot.fit.CurveDrawingMethod;
import edu.cnu.mdi.splot.pdata.Curve;
import edu.cnu.mdi.splot.pdata.PlotData;
import edu.cnu.mdi.splot.pdata.PlotDataException;
import edu.cnu.mdi.splot.pdata.PlotDataType;
import edu.cnu.mdi.splot.plot.PlotCanvas;
import edu.cnu.mdi.splot.plot.PlotPanel;

/**
 * Transient diagnostic for the unmodified Jupiter-slingshot preset.
 *
 * <p>The speed and specific orbital energy are measured in the heliocentric
 * frame. The planet-distance plot includes a separate symbol at the detected
 * closest approach. Because spacecraft mass is negligible, specific energy is
 * more useful here than the craft's extremely small total energy.</p>
 */
final class SlingshotPlotDialog extends JDialog {

    private static final int SUN_ID = 0;
    private static final int PLANET_ID = 1;
    private static final int SPACECRAFT_ID = 2;

    private final Parameters parameters;
    private final Curve speedCurve;
    private final Curve energyCurve;
    private final Curve distanceCurve;
    private final Curve closestCurve;
    private final PlotCanvas speedCanvas;
    private final PlotCanvas energyCanvas;
    private final PlotCanvas distanceCanvas;
    private final JLabel summary = new JLabel("Waiting for the spacecraft to approach Jupiter…");

    private Sample previous;
    private Sample initial;
    private Sample closest;
    private Sample after;
    private boolean closestMarked;

    /**
     * Creates a modeless diagnostic owned by the application window.
     *
     * @param owner owning application window
     * @param parameters physical parameters of the preset run
     */
    SlingshotPlotDialog(Window owner, Parameters parameters) {
        super(owner, "Jupiter gravitational assist", ModalityType.MODELESS);
        this.parameters = parameters;
        try {
            Plot speedPlot = createPlot(
                    "Spacecraft heliocentric speed", "Speed", "Speed");
            speedCurve = speedPlot.primary();
            speedCanvas = speedPlot.canvas();

            Plot energyPlot = createPlot(
                    "Spacecraft heliocentric specific energy", "Specific energy", "Specific energy");
            energyCurve = energyPlot.primary();
            energyCanvas = energyPlot.canvas();

            Plot distancePlot = createDistancePlot();
            distanceCurve = distancePlot.primary();
            closestCurve = distancePlot.secondary();
            distanceCanvas = distancePlot.canvas();
        } catch (PlotDataException ex) {
            throw new IllegalStateException("Could not create slingshot diagnostic plots", ex);
        }

        JPanel plots = new JPanel(new GridLayout(3, 1, 0, 3));
        plots.add(new PlotPanel(speedCanvas, PlotPanel.BARE));
        plots.add(new PlotPanel(energyCanvas, PlotPanel.BARE));
        plots.add(new PlotPanel(distanceCanvas, PlotPanel.BARE));

        setLayout(new BorderLayout());
        add(plots, BorderLayout.CENTER);
        summary.setBorder(BorderFactory.createEmptyBorder(5, 8, 7, 8));
        add(summary, BorderLayout.SOUTH);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(720, 720);
        setLocationByPlatform(true);
    }

    /**
     * Adds one immutable simulation state to each diagnostic curve.
     *
     * @param snapshot current simulation snapshot
     */
    void accept(Snapshot snapshot) {
        Body sun = find(snapshot, SUN_ID);
        Body planet = find(snapshot, PLANET_ID);
        Body spacecraft = find(snapshot, SPACECRAFT_ID);
        if (sun == null || planet == null || spacecraft == null) {
            return;
        }

        double relativeVx = spacecraft.vx() - sun.vx();
        double relativeVy = spacecraft.vy() - sun.vy();
        double speed = Math.hypot(relativeVx, relativeVy);
        double sunDx = spacecraft.x() - sun.x();
        double sunDy = spacecraft.y() - sun.y();
        double softenedRadius = Math.sqrt(sunDx * sunDx + sunDy * sunDy
                + parameters.epsilon() * parameters.epsilon());
        double specificEnergy = 0.5 * speed * speed
                - parameters.g() * sun.mass() / softenedRadius;
        double planetDistance = Math.hypot(
                spacecraft.x() - planet.x(), spacecraft.y() - planet.y());

        Sample current = new Sample(
                snapshot.time(), speed, specificEnergy, planetDistance);
        if (initial == null) {
            initial = current;
            closest = current;
        }

        speedCurve.add(current.time(), current.speed());
        energyCurve.add(current.time(), current.specificEnergy());
        distanceCurve.add(current.time(), current.planetDistance());

        if (current.planetDistance() < closest.planetDistance()) {
            closest = current;
        }
        if (!closestMarked && previous != null
                && previous.planetDistance() == closest.planetDistance()
                && current.planetDistance() > previous.planetDistance()) {
            closestCurve.add(closest.time(), closest.planetDistance());
            closestMarked = true;
        }
        if (after == null && closestMarked
                && current.planetDistance() >= initial.planetDistance()) {
            after = current;
        }

        updateSummary(current);
        previous = current;
        speedCanvas.repaint();
        energyCanvas.repaint();
        distanceCanvas.repaint();
    }

    private void updateSummary(Sample current) {
        Sample comparison = after == null ? current : after;
        double speedChange = comparison.speed() - initial.speed();
        double energyChange = comparison.specificEnergy() - initial.specificEnergy();
        String phase = after != null ? "assist complete"
                : closestMarked ? "outbound" : "approaching";
        String orbit = initial.specificEnergy() < 0 && comparison.specificEnergy() > 0
                ? "   BOUND → ESCAPE"
                : "";
        summary.setText(String.format(Locale.ROOT,
                "%s%s   closest: %.4g at t=%.3f   Δspeed: %+.4g   Δspecific energy: %+.4g",
                phase, orbit, closest.planetDistance(), closest.time(),
                speedChange, energyChange));
    }

    private static Plot createPlot(String title, String yLabel, String curveName)
            throws PlotDataException {
        PlotData data = new PlotData(PlotDataType.XYEXYE, new String[] {curveName}, null);
        Curve curve = (Curve) data.getCurve(0);
        configureLine(curve, new Color(35, 100, 195));
        PlotCanvas canvas = new PlotCanvas(data, title, "Simulation time", yLabel);
        return new Plot(curve, null, canvas);
    }

    private static Plot createDistancePlot() throws PlotDataException {
        PlotData data = new PlotData(PlotDataType.XYEXYE,
                new String[] {"Distance", "Closest approach"}, null);
        Curve distance = (Curve) data.getCurve(0);
        configureLine(distance, new Color(25, 135, 65));

        Curve closest = (Curve) data.getCurve(1);
        closest.setCurveDrawingMethod(CurveDrawingMethod.NONE);
        closest.getStyle().setSymbolType(SymbolType.CIRCLE);
        closest.getStyle().setSymbolSize(8);
        closest.getStyle().setFillColor(new Color(200, 55, 40));
        closest.getStyle().setLineColor(new Color(200, 55, 40));

        PlotCanvas canvas = new PlotCanvas(data,
                "Spacecraft distance from Jupiter", "Simulation time", "Distance");
        return new Plot(distance, closest, canvas);
    }

    private static void configureLine(Curve curve, Color color) {
        curve.setCurveDrawingMethod(CurveDrawingMethod.CONNECT);
        curve.getStyle().setSymbolType(SymbolType.NOSYMBOL);
        curve.getStyle().setLineColor(color);
    }

    private static Body find(Snapshot snapshot, int id) {
        return snapshot.bodies().stream()
                .filter(body -> body.id() == id)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void dispose() {
        speedCanvas.shutDown();
        energyCanvas.shutDown();
        distanceCanvas.shutDown();
        super.dispose();
    }

    /** Plot components created together from a single data model. */
    private record Plot(Curve primary, Curve secondary, PlotCanvas canvas) { }

    /** One set of spacecraft diagnostics sampled at a simulation time. */
    private record Sample(
            double time, double speed, double specificEnergy, double planetDistance) { }
}
