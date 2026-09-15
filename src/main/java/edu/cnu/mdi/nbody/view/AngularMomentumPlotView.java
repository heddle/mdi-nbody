package edu.cnu.mdi.nbody.view;

import edu.cnu.mdi.nbody.model.Snapshot;

public final class AngularMomentumPlotView extends DiagnosticPlotView {
    private double baseline;
    public AngularMomentumPlotView() {
        super("Angular momentum error", "ΔLz / |Lz₀|", "Relative angular-momentum error");
    }
    public void accept(Snapshot s) {
        double angularMomentum=s.diagnostics().angularMomentum();
        if (s.step()==0) baseline=angularMomentum;
        double error=Math.abs(baseline)>1e-14 ? (angularMomentum-baseline)/Math.abs(baseline) : Double.NaN;
        addSample(s.time(),error);
    }
}
