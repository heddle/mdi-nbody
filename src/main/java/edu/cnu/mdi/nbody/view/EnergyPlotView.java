package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import edu.cnu.mdi.nbody.model.Snapshot;

/** Plots energy components or relative total-energy error over time. */
public final class EnergyPlotView extends DiagnosticPlotView {
    /** Total energy at step zero of the current run. */
    private double baseline;

    /** Creates the energy view and its component/error display toggle. */
    public EnergyPlotView() {
        super("Energy", "Energy", "Kinetic", "Potential", "Total", "Relative error");
        curves[3].setVisible(false);
        var relative = new JCheckBox("Show relative total-energy error");
        relative.setToolTipText("(E − E₀) / |E₀|; undefined when initial energy is approximately zero");
        relative.addActionListener(event -> {
            for (int i = 0; i < 3; i++) {
                curves[i].setVisible(!relative.isSelected());
            }
            curves[3].setVisible(relative.isSelected());
            _plotCanvas.getParameters().setYLabel(relative.isSelected() ? "ΔE / |E₀|" : "Energy");
            _plotCanvas.repaint();
        });
        add(relative, BorderLayout.SOUTH);
    }
    /**
     * Adds the energy diagnostics represented by a snapshot.
     *
     * @param snapshot immutable simulation state
     */
    public void accept(Snapshot snapshot) {
        var diagnostics = snapshot.diagnostics();
        if (snapshot.step() == 0) {
            baseline = diagnostics.energy();
        }
        double error = Math.abs(baseline) > 1e-14
                ? (diagnostics.energy() - baseline) / Math.abs(baseline)
                : Double.NaN;
        addSample(snapshot.time(), diagnostics.kinetic(), diagnostics.potential(),
                diagnostics.energy(), error);
    }
}
