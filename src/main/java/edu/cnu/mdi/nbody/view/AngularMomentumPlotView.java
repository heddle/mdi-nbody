package edu.cnu.mdi.nbody.view;

import edu.cnu.mdi.nbody.model.Snapshot;

/** Plots relative angular-momentum error against simulation time. */
public final class AngularMomentumPlotView extends DiagnosticPlotView {
    /** Angular momentum at step zero of the current run. */
    private double baseline;

    /** Creates the angular-momentum diagnostic view. */
    public AngularMomentumPlotView() {
        super("Angular momentum error", "ΔLz / |Lz₀|", "Relative angular-momentum error");
    }
    /**
     * Adds the angular-momentum error represented by a snapshot.
     *
     * @param snapshot immutable simulation state
     */
    public void accept(Snapshot snapshot) {
        double angularMomentum = snapshot.diagnostics().angularMomentum();
        if (snapshot.step() == 0) {
            baseline = angularMomentum;
        }
        double error = Math.abs(baseline) > 1e-14
                ? (angularMomentum - baseline) / Math.abs(baseline)
                : Double.NaN;
        addSample(snapshot.time(), error);
    }
}
