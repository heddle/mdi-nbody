package edu.cnu.mdi.nbody.view;

import java.awt.Color;
import edu.cnu.mdi.graphics.style.SymbolType;
import edu.cnu.mdi.splot.fit.CurveDrawingMethod;
import edu.cnu.mdi.splot.pdata.*;
import edu.cnu.mdi.splot.plot.*;
import edu.cnu.mdi.util.PropertyUtils;

/** Shared sPlot setup. Retains a bounded block of display samples. All calls are on the EDT. */
abstract class DiagnosticPlotView extends PlotView {
    protected final Curve[] curves;
    private int samples;
    private static final int MAX_SAMPLES = 10000;
    DiagnosticPlotView(String title, String ylabel, String... names) {
        super(PropertyUtils.TITLE, title, PropertyUtils.WIDTH, 520, PropertyUtils.HEIGHT, 310,
                PropertyUtils.VISIBLE, true, PropertyUtils.ADDFEEDBACK, false);
        try {
            var data = new PlotData(PlotDataType.XYEXYE, names, null);
            curves = new Curve[names.length];
            Color[] colors = {new Color(20,125,60), new Color(40,95,205), new Color(195,55,40), Color.MAGENTA.darker()};
            for (int i = 0; i < names.length; i++) {
                curves[i] = (Curve)data.getCurve(names[i]);
                curves[i].setCurveDrawingMethod(CurveDrawingMethod.CONNECT);
                curves[i].getStyle().setSymbolType(SymbolType.NOSYMBOL);
                curves[i].getStyle().setLineColor(colors[i % colors.length]);
            }
            var canvas = new PlotCanvas(data, title, "Simulation time", ylabel);
            canvas.getParameters().setNumDecimalY(2).setMinExponentY(3);
            canvas.getPlotTicks().setNumMajorTickY(3);
            canvas.getPlotTicks().setNumMinorTickY(2);
            canvas.getPlotTicks().setTickFont(edu.cnu.mdi.ui.fonts.Fonts.smallFont);
            PlotParameters params = canvas.getParameters();
            params.setMinExponentY(6).setNumDecimalY(2);

            switchToPlotPanel(createDecoratedPlotPanel(canvas));
        } catch (PlotDataException ex) { throw new IllegalStateException(ex); }
    }
    protected void addSample(double time, double... values) {
        if (samples == MAX_SAMPLES) clear();
        for (int i = 0; i < curves.length; i++) if (Double.isFinite(values[i])) curves[i].add(time, values[i]);
        samples++;
    }
    public void clear() { for (Curve c : curves) c.clearData(); samples = 0; }
    @Override public void prepareForExit() { _plotCanvas.shutDown(); super.prepareForExit(); }
}
