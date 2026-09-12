package edu.cnu.mdi.nbody.view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.*;
import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.graphics.drawable.DrawableAdapter;
import edu.cnu.mdi.graphics.toolbar.*;
import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.nbody.sim.NBodySimulation;
import edu.cnu.mdi.sim.*;
import edu.cnu.mdi.sim.ui.SimulationView;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.AbstractViewInfo;

/** World-coordinate MDI view; all rendering and history use EDT-owned immutable snapshots. */
public final class NBodyView extends SimulationView {
    private final EnergyPlotView energy;
    private final AngularMomentumPlotView angular;
    private final BodyInfoView info;
    private final ArrayDeque<Snapshot> history = new ArrayDeque<>();
    private Snapshot shown;
    private int selected = 0, trailLength = 500, updateHz = 30;
    private boolean trails = true, velocities, com = true, closing;
    private double initialEnergy;
    private final JLabel diagnosticsLabel = new JLabel();

    public NBodyView(EnergyPlotView energy, AngularMomentumPlotView angular, BodyInfoView info) {
        super(new NBodySimulation(Presets.create(Presets.Preset.CIRCULAR,Parameters.defaults(),20,42),Parameters.defaults()),
                new SimulationEngineConfig(0,0,0,true), true, NBodyControls::new,
                PropertyUtils.TITLE,"N-body simulation", PropertyUtils.WIDTH,850, PropertyUtils.HEIGHT,650,
                PropertyUtils.WORLDSYSTEM,new Rectangle2D.Double(-5,-4,10,8), PropertyUtils.VISIBLE,true,
                PropertyUtils.TOOLBARBITS,ToolBits.POINTER | ToolBits.PAN | ToolBits.BOXZOOM | ToolBits.ZOOMIN
                        | ToolBits.ZOOMOUT | ToolBits.RESETZOOM | ToolBits.UNDOZOOM | ToolBits.INFO);
        this.energy = energy; this.angular = angular; this.info = info;
        JPanel south = new JPanel(new BorderLayout());
        var controlsScroll = new JScrollPane(controlPanel,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        controlsScroll.setBorder(null);
        controlsScroll.setPreferredSize(new Dimension(0,controlPanel.getPreferredSize().height+20));
        south.add(controlsScroll,BorderLayout.CENTER); south.add(diagnosticsLabel,BorderLayout.SOUTH); add(south,BorderLayout.SOUTH);
        simulation().bind(engine);
        initializeSnapshot();
        getIContainer().setAfterDraw(new DrawableAdapter() {
            @Override public void draw(Graphics2D g, IContainer c) { drawBodies(g,c); }
        });
        getIContainer().getComponent().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || !(getToolBar() instanceof BaseToolBar tb) || !tb.isPointerActive()) return;
                Point point = new Point(); double nearest = Double.POSITIVE_INFINITY; int id = -1;
                for (Body b : shown.bodies()) {
                    getIContainer().worldToLocal(point,b.x(),b.y()); double distance = point.distance(event.getPoint());
                    if (distance <= radius(b)+4 && distance < nearest) { nearest = distance; id = b.id(); }
                }
                selected = id; info.accept(shown,selected); repaint();
            }
        });
        getIContainer().getComponent().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { equalAxes(); }
        });
        startSimulation();
    }
    private void equalAxes() {
        var c = getIContainer();
        var size = c.getComponent().getSize();
        if (size.width <= 0 || size.height <= 0) return;
        Rectangle2D.Double world = c.getWorldSystem();
        double width = world.height * size.width / size.height;
        c.setWorldSystem(new Rectangle2D.Double(world.getCenterX()-width/2,world.y,width,world.height));
    }
    private NBodySimulation simulation() { return (NBodySimulation)engine.getSimulation(); }
    private void initializeSnapshot() {
        shown = simulation().snapshot(); initialEnergy = shown.diagnostics().energy(); history.clear();
        energy.clear(); angular.clear(); selected = 0; accept(shown);
    }
    public void reset(List<Body> initial, Parameters p) {
        if (closing) return;
        requestEngineReset(() -> new NBodySimulation(initial,p), next -> {
            simulation().bind(next); simulation().setUpdateHz(updateHz); initializeSnapshot();
            double extent = initial.stream().mapToDouble(b -> Math.max(Math.abs(b.x()),Math.abs(b.y()))).max().orElse(2);
            extent = Math.max(2,extent*1.4);
            getIContainer().resetWorldSystem(new Rectangle2D.Double(-extent,-extent,2*extent,2*extent));
            equalAxes();
            getIContainer().resetWorldSystem(getIContainer().getWorldSystem());
        },true,true);
    }
    public void singleStep() {
        var state = engine.getState();
        if (state == SimulationState.READY || state == SimulationState.PAUSED) simulation().requestSingleStep();
    }
    public void setUpdateHz(int hz) { updateHz = hz; simulation().setUpdateHz(hz); }
    public void setDisplay(boolean trails,boolean velocities,boolean com,int length) {
        this.trails=trails; this.velocities=velocities; this.com=com; trailLength=length;
        trimHistory(); repaint();
    }
    private void trimHistory() { while (history.size() > trailLength) history.removeFirst(); }
    private void accept(Snapshot s) {
        shown = s; history.addLast(s); trimHistory(); energy.accept(s); angular.accept(s); info.accept(s,selected);
        var d = s.diagnostics();
        String error = Math.abs(initialEnergy) > 1e-14
                ? String.format(java.util.Locale.ROOT,"%+.3e",(d.energy()-initialEnergy)/Math.abs(initialEnergy)) : "undefined (E₀ ≈ 0)";
        diagnosticsLabel.setText(String.format(java.util.Locale.ROOT,
                " t=%.4f   ΔE/|E₀|=%s   P=(%.3g, %.3g)   COM=(%.3g, %.3g)",s.time(),error,d.px(),d.py(),d.comX(),d.comY()));
    }
    @Override protected void onSimulationRefresh(SimulationContext ctx) {
        if (closing || shown == null || ctx != engine.getContext()) return;
        Snapshot s = simulation().snapshot();
        if (s.step() != shown.step()) accept(s);
    }
    @Override protected void onSimulationPause(SimulationContext ctx) { onSimulationRefresh(ctx); }
    @Override protected void onSimulationDone(SimulationContext ctx) { onSimulationRefresh(ctx); }
    private static int radius(Body b) { return (int)Math.max(4,Math.min(14,7*Math.cbrt(b.mass()))); }
    private static Color color(int id) { return Color.getHSBColor((float)((id*.61803398875)%1),.7f,.8f); }
    private void drawBodies(Graphics2D original, IContainer c) {
        if (shown == null) return;
        Graphics2D g = (Graphics2D)original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Point a = new Point(), b = new Point();
            if (trails) {
                Snapshot previous = null;
                for (Snapshot s : history) {
                    if (previous != null) for (int i=0;i<s.bodies().size();i++) {
                        Body old=previous.bodies().get(i), now=s.bodies().get(i);
                        c.worldToLocal(a,old.x(),old.y()); c.worldToLocal(b,now.x(),now.y());
                        Color col=color(now.id()); g.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),110));
                        g.drawLine(a.x,a.y,b.x,b.y);
                    }
                    previous=s;
                }
            }
            for (Body body : shown.bodies()) {
                c.worldToLocal(a,body.x(),body.y()); int r=radius(body);
                g.setColor(color(body.id())); g.fillOval(a.x-r,a.y-r,2*r,2*r);
                if (body.id()==selected) { g.setColor(Color.BLACK); g.drawOval(a.x-r-3,a.y-r-3,2*r+6,2*r+6); }
                if (velocities) {
                    c.worldToLocal(b,body.x()+body.vx()*.4,body.y()+body.vy()*.4);
                    g.drawLine(a.x,a.y,b.x,b.y);
                    double angle=Math.atan2(b.y-a.y,b.x-a.x);
                    for (double delta : new double[]{-.45,.45}) g.drawLine(b.x,b.y,
                            b.x-(int)(8*Math.cos(angle+delta)),b.y-(int)(8*Math.sin(angle+delta)));
                }
            }
            if (com) {
                c.worldToLocal(a,shown.diagnostics().comX(),shown.diagnostics().comY()); g.setColor(Color.DARK_GRAY);
                g.drawLine(a.x-8,a.y,a.x+8,a.y); g.drawLine(a.x,a.y-8,a.x,a.y+8); g.drawString("COM",a.x+10,a.y-5);
            }
        } finally { g.dispose(); }
    }
    @Override public AbstractViewInfo getViewInfo() {
        return new AbstractViewInfo() {
            @Override public String getTitle() { return "N-body simulation"; }
            @Override public String getPurpose() { return "Explore softened Newtonian gravity in dimensionless units."; }
            @Override public List<String> getUsageBullets() { return List.of("Start, pause, resume, or single-step the simulation.",
                    "Change physics settings and press Reset to start a new run.","Use the MDI pan and zoom tools; use the pointer to select a body.",
                    "Trails and plots sample at the display rate. Velocity arrows show v × 0.4 time units."); }
        };
    }
    @Override public void onStateChange(SimulationContext ctx, SimulationState from, SimulationState to, String reason) {
        if (!closing && ctx == engine.getContext()) super.onStateChange(ctx,from,to,reason);
    }
    @Override public void prepareForExit() {
        if (closing) return;
        closing=true; engine.requestStop(); engine.removeListener(this);
        ((NBodyControls)controlPanel).unbind(); history.clear(); super.prepareForExit();
    }
    @Override public void dispose() { prepareForExit(); super.dispose(); }
}
