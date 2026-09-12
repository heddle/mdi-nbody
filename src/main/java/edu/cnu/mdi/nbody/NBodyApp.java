package edu.cnu.mdi.nbody;

import edu.cnu.mdi.app.BaseMDIApplication;
import edu.cnu.mdi.nbody.view.*;
import edu.cnu.mdi.util.PropertyUtils;

/** Compact MDI demonstration with one simulation and three companion views. */
public final class NBodyApp extends BaseMDIApplication {
    private NBodyView bodies;
    private EnergyPlotView energy;
    private AngularMomentumPlotView angular;
    private BodyInfoView info;
    public NBodyApp(Object... keyVals) { super(keyVals); }
    @Override protected void addInitialViews() {
        // Called by the superclass constructor: do not depend on subclass field initializers.
        energy = new EnergyPlotView();
        angular = new AngularMomentumPlotView();
        info = new BodyInfoView();
        bodies = new NBodyView(energy,angular,info);

    }
    @Override protected void onVirtualDesktopReady() {
        super.onVirtualDesktopReady();
        // MDI calls defaultViewLayout automatically only when a virtual desktop is enabled.
        defaultViewLayout();
        validate();
    }
    @Override protected void defaultViewLayout() {
        var desktop = edu.cnu.mdi.desktop.Desktop.getInstance();
        int w = desktop.getWidth(), h = desktop.getHeight(), left = (int)(w*.60);
        if (!hasSavedLayout(bodies)) bodies.setBounds(5,5,left-10,h-10);
        int right = w-left-5, third = (h-20)/3;
        if (!hasSavedLayout(energy)) energy.setBounds(left,5,right,third);
        if (!hasSavedLayout(angular)) angular.setBounds(left,10+third,right,third);
        if (!hasSavedLayout(info)) info.setBounds(left,15+2*third,right,third);
    }
    @Override protected boolean exitOnClose() { return true; }
    public static void main(String[] args) {
        BaseMDIApplication.launch(() -> new NBodyApp(PropertyUtils.TITLE,"MDI N-body",PropertyUtils.FRACTION,.95));
    }
}
