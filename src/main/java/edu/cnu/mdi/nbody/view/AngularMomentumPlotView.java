package edu.cnu.mdi.nbody.view;

import edu.cnu.mdi.nbody.model.Snapshot;

public final class AngularMomentumPlotView extends DiagnosticPlotView {
    public AngularMomentumPlotView() { super("Angular momentum", "Lz", "Lz"); }
    public void accept(Snapshot s) { addSample(s.time(), s.diagnostics().angularMomentum()); }
}
