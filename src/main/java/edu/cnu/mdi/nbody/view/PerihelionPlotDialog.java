package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;

import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.nbody.model.Snapshot;
import edu.cnu.mdi.splot.fit.CurveDrawingMethod;
import edu.cnu.mdi.splot.pdata.Curve;
import edu.cnu.mdi.splot.pdata.PlotData;
import edu.cnu.mdi.splot.pdata.PlotDataException;
import edu.cnu.mdi.splot.pdata.PlotDataType;
import edu.cnu.mdi.splot.plot.PlotCanvas;
import edu.cnu.mdi.splot.plot.PlotPanel;

/**
 * Transient diagnostic for an unmodified Sun-Mercury-Jupiter experiment.
 * Successive local minima of the Sun-Mercury distance identify perihelia; the
 * dialog plots the unwrapped longitude relative to the first detected passage.
 */
final class PerihelionPlotDialog extends JDialog {

    private final Curve curve;
    private final PlotCanvas canvas;
    private final JLabel summary = new JLabel("Waiting for perihelion passages…");
    private Sample older;
    private Sample previous;
    private double lastRawAngle;
    private double unwrappedAngle;
    private double baselineAngle;
    private int passages;

    PerihelionPlotDialog(Window owner) {
        super(owner, "Mercury perihelion precession", ModalityType.MODELESS);
        try {
            PlotData data = new PlotData(
                    PlotDataType.XYEXYE, new String[] {"Perihelion angle"}, null);
            curve = (Curve) data.getCurve(0);
            curve.setCurveDrawingMethod(CurveDrawingMethod.CONNECT);
            canvas = new PlotCanvas(data,
                    "Mercury perihelion precession",
                    "Perihelion passage",
                    "Δ longitude (degrees)");
        } catch (PlotDataException ex) {
            throw new IllegalStateException("Could not create perihelion plot", ex);
        }

        setLayout(new BorderLayout());
        add(new PlotPanel(canvas, PlotPanel.BARE), BorderLayout.CENTER);
        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        add(summary, BorderLayout.SOUTH);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(680, 430);
        setLocationByPlatform(true);
    }

    void accept(Snapshot snapshot) {
        Body sun = find(snapshot, 0);
        Body mercury = find(snapshot, 1);
        if (sun == null || mercury == null) {
            return;
        }

        double dx = mercury.x() - sun.x();
        double dy = mercury.y() - sun.y();
        Sample current = new Sample(snapshot.time(), Math.hypot(dx, dy), Math.atan2(dy, dx));
        if (older != null && previous.distance() < older.distance()
                && previous.distance() < current.distance()) {
            addPerihelion(previous);
        }
        older = previous;
        previous = current;
    }

    private void addPerihelion(Sample sample) {
        if (passages == 0) {
            lastRawAngle = sample.angle();
            unwrappedAngle = sample.angle();
            baselineAngle = sample.angle();
        } else {
            double delta = sample.angle() - lastRawAngle;
            while (delta > Math.PI) {
                delta -= 2 * Math.PI;
            }
            while (delta < -Math.PI) {
                delta += 2 * Math.PI;
            }
            unwrappedAngle += delta;
            lastRawAngle = sample.angle();
        }

        double degrees = Math.toDegrees(unwrappedAngle - baselineAngle);
        curve.add(++passages, degrees);
        canvas.repaint();
        summary.setText(String.format(Locale.ROOT,
                "%d passages   latest Δ longitude: %+.6g°   "
                        + "Newtonian perturbation; numerical settings also contribute.",
                passages, degrees));
    }

    private static Body find(Snapshot snapshot, int id) {
        return snapshot.bodies().stream()
                .filter(body -> body.id() == id)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void dispose() {
        canvas.shutDown();
        super.dispose();
    }

    /** One sampled polar position of Mercury relative to the Sun. */
    private record Sample(double time, double distance, double angle) { }
}
