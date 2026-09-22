package edu.cnu.mdi.nbody;

import edu.cnu.mdi.app.BaseMDIApplication;
import edu.cnu.mdi.nbody.view.*;
import edu.cnu.mdi.util.PropertyUtils;

/**
 * Top-level MDI application for the N-body example.
 *
 * <p>The application creates one interactive simulation view and three linked
 * diagnostic views, then arranges them in a reproducible default layout while
 * respecting any layout previously saved by MDI.</p>
 */
public final class NBodyApp extends BaseMDIApplication {
    /** Primary interactive simulation view. */
    private NBodyView bodies;
    /** Linked energy diagnostic. */
    private EnergyPlotView energy;
    /** Linked angular-momentum diagnostic. */
    private AngularMomentumPlotView angular;
    /** Linked selected-body inspector. */
    private BodyInfoView info;
    /**
     * Creates the application using standard MDI property key/value pairs.
     *
     * @param keyVals application properties understood by {@link BaseMDIApplication}
     */
    public NBodyApp(Object... keyVals) {
        super(keyVals);
    }

    @Override
    protected void addInitialViews() {
        // Called by the superclass constructor: do not depend on subclass field initializers.
        energy = new EnergyPlotView();
        angular = new AngularMomentumPlotView();
        info = new BodyInfoView();
        bodies = new NBodyView(energy, angular, info);
    }

    @Override
    protected void onVirtualDesktopReady() {
        super.onVirtualDesktopReady();
        // MDI calls defaultViewLayout automatically only when a virtual desktop is enabled.
        defaultViewLayout();
        validate();
    }
    @Override
    protected void defaultViewLayout() {
        var desktop = edu.cnu.mdi.desktop.Desktop.getInstance();
        int width = desktop.getWidth();
        int height = desktop.getHeight();
        int leftWidth = (int) (width * 0.60);
        if (!hasSavedLayout(bodies)) {
            bodies.setBounds(5, 5, leftWidth - 10, height - 10);
        }
        int rightWidth = width - leftWidth - 5;
        int thirdHeight = (height - 20) / 3;
        if (!hasSavedLayout(energy)) {
            energy.setBounds(leftWidth, 5, rightWidth, thirdHeight);
        }
        if (!hasSavedLayout(angular)) {
            angular.setBounds(leftWidth, 10 + thirdHeight, rightWidth, thirdHeight);
        }
        if (!hasSavedLayout(info)) {
            info.setBounds(leftWidth, 15 + 2 * thirdHeight, rightWidth, thirdHeight);
        }
    }

    @Override
    protected boolean exitOnClose() {
        return true;
    }

    /**
     * Launches the Swing application on the event-dispatch thread.
     *
     * @param args command-line arguments; currently unused
     */
    public static void main(String[] args) {
        BaseMDIApplication.launch(() -> new NBodyApp(
                PropertyUtils.TITLE, "MDI N-body",
                PropertyUtils.FRACTION, 0.95));
    }
}
