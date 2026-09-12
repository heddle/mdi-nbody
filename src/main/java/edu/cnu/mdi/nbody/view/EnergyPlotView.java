package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import edu.cnu.mdi.nbody.model.Snapshot;

public final class EnergyPlotView extends DiagnosticPlotView {
    private double baseline;
    public EnergyPlotView() {
        super("Energy", "Energy", "Kinetic", "Potential", "Total", "Relative error");
        curves[3].setVisible(false);
        var relative = new JCheckBox("Show relative total-energy error");
        relative.setToolTipText("(E − E₀) / |E₀|; undefined when initial energy is approximately zero");
        relative.addActionListener(event -> {
            for (int i=0;i<3;i++) curves[i].setVisible(!relative.isSelected());
            curves[3].setVisible(relative.isSelected());
            _plotCanvas.getParameters().setYLabel(relative.isSelected() ? "ΔE / |E₀|" : "Energy");
            _plotCanvas.repaint();
        });
        add(relative, BorderLayout.SOUTH);
    }
    public void accept(Snapshot s) {
        var d = s.diagnostics();
        if (s.step() == 0) baseline = d.energy();
        double error = Math.abs(baseline) > 1e-14 ? (d.energy()-baseline)/Math.abs(baseline) : Double.NaN;
        addSample(s.time(), d.kinetic(), d.potential(), d.energy(), error);
    }
}
