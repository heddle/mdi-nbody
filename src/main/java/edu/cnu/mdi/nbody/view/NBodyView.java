package edu.cnu.mdi.nbody.view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import javax.swing.*;
import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.graphics.drawable.DrawableAdapter;
import edu.cnu.mdi.graphics.toolbar.*;
import edu.cnu.mdi.item.*;
import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.nbody.sim.NBodySimulation;
import edu.cnu.mdi.sim.*;
import edu.cnu.mdi.sim.ui.SimulationView;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.AbstractViewInfo;

/** World-coordinate MDI view; all rendering and history use EDT-owned immutable snapshots. */
public final class NBodyView extends SimulationView implements ItemChangeListener {
    private final EnergyPlotView energy;
    private final AngularMomentumPlotView angular;
    private final BodyInfoView info;
    private final ArrayDeque<Snapshot> history = new ArrayDeque<>();
    private final List<BodyItem> bodyItems = new ArrayList<>();
    private Snapshot shown;
    private int selected = 0, trailLength = 500, updateHz = 30;
    private Integer pendingSelection;
    private boolean trails = true, velocities, com = true, closing, rebuildingItems;
    private double initialEnergy;
    private Parameters setupParameters=Parameters.defaults();
    private boolean perihelionExperiment;
    private PerihelionPlotDialog perihelionDialog;
    private NBodySetup setup,initialSetup;
    private Presets.Preset setupPreset=Presets.Preset.CIRCULAR;
    private boolean editingSetup,placingBody,runAfterReset;
    private final JLabel diagnosticsLabel = new JLabel();

    public NBodyView(EnergyPlotView energy, AngularMomentumPlotView angular, BodyInfoView info) {
        super(new NBodySimulation(Presets.create(Presets.Preset.CIRCULAR,Parameters.defaults(),20,42),Parameters.defaults()),
                new SimulationEngineConfig(0,0,0,true), true, NBodyControls::new,
                PropertyUtils.TITLE,"N-body simulation", PropertyUtils.WIDTH,850, PropertyUtils.HEIGHT,650,
                PropertyUtils.WORLDSYSTEM,new Rectangle2D.Double(-5,-4,10,8), PropertyUtils.VISIBLE,true,
                PropertyUtils.TOOLBARBITS,ToolBits.POINTER | ToolBits.PAN | ToolBits.BOXZOOM | ToolBits.ZOOMIN
                        | ToolBits.ZOOMOUT | ToolBits.RESETZOOM | ToolBits.UNDOZOOM | ToolBits.CAMERA | ToolBits.INFO);
        this.energy = energy; this.angular = angular; this.info = info;
        JPanel south = new JPanel(new BorderLayout());
        var controlsScroll = new JScrollPane(controlPanel,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        controlsScroll.setBorder(null);
        controlsScroll.setPreferredSize(new Dimension(0,controlPanel.getPreferredSize().height+20));
        south.add(controlsScroll,BorderLayout.CENTER); south.add(diagnosticsLabel,BorderLayout.SOUTH); add(south,BorderLayout.SOUTH);
        simulation().bind(engine);
        getDefaultLayer().addItemChangeListener(this);
        initializeSnapshot();
        getIContainer().setBeforeDraw(new DrawableAdapter() {
            @Override public void draw(Graphics2D g, IContainer c) { drawBackground(g,c); }
        });
        getIContainer().getComponent().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { equalAxes(); }
        });
        getIContainer().getComponent().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!placingBody || !SwingUtilities.isLeftMouseButton(event) || event.getClickCount()!=1 || !canEdit()) return;
                placingBody=false;
                var point=new java.awt.geom.Point2D.Double(); getIContainer().localToWorld(event.getPoint(),point);
                enterSetup();
                Body body=new Body(setup.nextId(),1,point.x,point.y,0,0); setup.put(body);
                rebuildBodyItems(setup.bodies()); selectBody(body.id()); controls().setupChanged();
            }
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
        setup=new NBodySetup(shown.bodies(),setupParameters);
        initialSetup=new NBodySetup(shown.bodies(),setupParameters); editingSetup=false;
        energy.clear(); angular.clear();
        selected=pendingSelection!=null && shown.bodies().stream().anyMatch(body -> body.id()==pendingSelection)
                ? pendingSelection : shown.bodies().get(0).id();
        pendingSelection=null; rebuildBodyItems(shown.bodies()); accept(shown);
        bodyItems.stream().filter(item -> item.body().id()==selected).findFirst()
                .ifPresent(item -> getDefaultLayer().selectItem(item,true));
    }
    private void rebuildBodyItems(List<Body> bodies) {
        rebuildingItems=true;
        try {
            for (BodyItem item:List.copyOf(bodyItems)) getDefaultLayer().remove(item);
            bodyItems.clear();
            for (Body body:bodies) {
                BodyItem item=new BodyItem(getDefaultLayer(),body,this::editBody);
                item.setShowVelocity(velocities); bodyItems.add(item);
            }
        } finally { rebuildingItems=false; }
        updateItemLocks();
    }
    public void reset(List<Body> initial, Parameters p) {
        if (closing) return;
        setupParameters=p; placingBody=false;
        requestEngineReset(() -> new NBodySimulation(initial,p), next -> {
            simulation().bind(next); simulation().setUpdateHz(updateHz); initializeSnapshot();
            double extent = initial.stream().mapToDouble(b -> Math.max(Math.abs(b.x()),Math.abs(b.y()))).max().orElse(2);
            extent = Math.max(2,extent*1.4);
            getIContainer().resetWorldSystem(new Rectangle2D.Double(-extent,-extent,2*extent,2*extent));
            equalAxes();
            getIContainer().resetWorldSystem(getIContainer().getWorldSystem());
            updateItemLocks(true);
        },true,true);
    }
    public void resetPreset(Presets.Preset preset,List<Body> initial,Parameters p) {
        invalidateSpecialExperiment();
        setupPreset=preset;
        perihelionExperiment=preset==Presets.Preset.MERCURY_JUPITER
                && p.equals(Presets.mercuryPrecessionParameters());
        reset(initial,p);
    }
    public void restoreCurrentModel() {
        if (!canEdit() || initialSetup==null) return;
        invalidateSpecialExperiment();
        perihelionExperiment=setupPreset==Presets.Preset.MERCURY_JUPITER
                && initialSetup.parameters().equals(Presets.mercuryPrecessionParameters());
        controls().setParameters(initialSetup.parameters());
        reset(initialSetup.bodies(),initialSetup.parameters());
    }
    public void loadPreset(Presets.Preset preset,List<Body> bodies,Parameters parameters) {
        if (!canEdit() || preset==Presets.Preset.CUSTOM) return;
        invalidateSpecialExperiment(); controls().setParameters(parameters); setupPreset=preset; setup=new NBodySetup(bodies,parameters);
        setupParameters=parameters; editingSetup=true; placingBody=false; history.clear(); energy.clear(); angular.clear();
        rebuildBodyItems(setup.bodies()); selected=setup.bodies().get(0).id(); selectBody(selected);
        diagnosticsLabel.setText(" Preset loaded. Edit it or press Start."); controls().setupChanged(); repaint();
    }
    public void invalidateSpecialExperiment() {
        perihelionExperiment=false;
        if (perihelionDialog!=null) { perihelionDialog.dispose(); perihelionDialog=null; }
    }
    public void singleStep() {
        if (editingSetup) return;
        var state = engine.getState();
        if (state == SimulationState.READY || state == SimulationState.PAUSED) simulation().requestSingleStep();
    }
    public void setUpdateHz(int hz) { updateHz = hz; simulation().setUpdateHz(hz); }
    public void setDisplay(boolean trails,boolean velocities,boolean com,int length) {
        this.trails=trails; this.velocities=velocities; this.com=com; trailLength=length;
        bodyItems.forEach(item -> item.setShowVelocity(velocities));
        trimHistory(); repaint();
    }
    public void physicsChanged() {
        if (!canEdit()) return;
        try {
            enterSetup(); setup.setParameters(controls().parameters()); setupParameters=setup.parameters();
            diagnosticsLabel.setText(" Physics changed. Press Start to begin the custom setup.");
        } catch (IllegalArgumentException ex) { showError("Invalid settings",ex); }
    }
    public void addBody() {
        if (!canEdit() || bodyItems.size()>=50) { Toolkit.getDefaultToolkit().beep(); return; }
        enterSetup(); placingBody=true; diagnosticsLabel.setText(" Click the canvas to place the new body.");
    }
    public void newModel() {
        if (!canEdit()) return;
        Parameters parameters;
        try { parameters=controls().parameters(); }
        catch (IllegalArgumentException ex) { showError("Invalid settings",ex); return; }
        invalidateSpecialExperiment(); setupPreset=Presets.Preset.CUSTOM; editingSetup=true; placingBody=false;
        setup=NBodySetup.empty(parameters); history.clear(); energy.clear(); angular.clear();
        rebuildBodyItems(List.of()); selected=-1;
        info.acceptSetup(null);
        diagnosticsLabel.setText(" Custom setup: click Add Body, then click the canvas.");
        controls().markCustom(); controls().setupChanged(); repaint();
    }
    public void startOrRun() {
        if (!editingSetup) { runSimulation(); return; }
        if (!setup.canRun()) { Toolkit.getDefaultToolkit().beep(); return; }
        try { setup.setParameters(controls().parameters()); }
        catch (IllegalArgumentException ex) { showError("Invalid settings",ex); return; }
        invalidateSpecialExperiment();
        perihelionExperiment=setupPreset==Presets.Preset.MERCURY_JUPITER
                && setup.parameters().equals(Presets.mercuryPrecessionParameters());
        runAfterReset=true;
        reset(setup.bodies(),setup.parameters());
    }
    public void saveModel() {
        NBodySetup current=editingSetup ? setup : new NBodySetup(editableBodies(),setupParameters);
        JFileChooser chooser=new JFileChooser(); chooser.setSelectedFile(new java.io.File("nbody-model.json"));
        if (chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        try { NBodySetupIO.write(chooser.getSelectedFile().toPath(),current); }
        catch (IOException ex) { showError("Could not save model",ex); }
    }
    public void openModel() {
        if (!canEdit()) return;
        JFileChooser chooser=new JFileChooser();
        if (chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        try {
            invalidateSpecialExperiment(); setup=NBodySetupIO.read(chooser.getSelectedFile().toPath());
            setupParameters=setup.parameters(); setupPreset=Presets.Preset.CUSTOM; editingSetup=true; placingBody=false;
            rebuildBodyItems(setup.bodies()); history.clear(); energy.clear(); angular.clear();
            selected=setup.bodies().isEmpty() ? -1 : setup.bodies().get(0).id();
            info.acceptSetup(selectedBody());
            controls().setParameters(setup.parameters()); controls().markCustom(); controls().setupChanged(); repaint();
        } catch (IOException ex) { showError("Could not open model",ex); }
    }
    public void showSetupTools() {
        if (!canEdit()) return; enterSetup();
        String[] choices={"Center COM at origin","Remove net momentum","Duplicate selected body",
                "Circular velocity around another body","Reverse all velocities","Scale positions","Scale velocities"};
        String choice=(String)JOptionPane.showInputDialog(this,"Choose an operation:","Setup Tools",
                JOptionPane.PLAIN_MESSAGE,null,choices,choices[0]);
        if (choice==null) return;
        try {
            switch (choice) {
                case "Center COM at origin" -> setup.centerOnMass();
                case "Remove net momentum" -> setup.removeNetMomentum();
                case "Duplicate selected body" -> { if (selected<0) throw new IllegalArgumentException("Select a body first"); setup.duplicate(selected); }
                case "Circular velocity around another body" -> circularVelocity();
                case "Reverse all velocities" -> setup.reverseVelocities();
                case "Scale positions" -> setup.scalePositions(promptScale("Position scale"));
                case "Scale velocities" -> setup.scaleVelocities(promptScale("Velocity scale"));
            }
            rebuildBodyItems(setup.bodies()); controls().setupChanged(); repaint();
        } catch (IllegalArgumentException ex) { showError("Cannot apply setup operation",ex); }
    }
    private void circularVelocity() {
        if (selected<0) throw new IllegalArgumentException("Select the orbiting body first");
        String input=JOptionPane.showInputDialog(this,"Primary body ID:");
        if (input==null) throw new IllegalArgumentException("Operation cancelled");
        setup.setCircularVelocity(selected,Integer.parseInt(input.trim()));
    }
    private double promptScale(String title) {
        String input=JOptionPane.showInputDialog(this,title + ":","1.0");
        if (input==null) throw new IllegalArgumentException("Operation cancelled"); return Double.parseDouble(input.trim());
    }
    private void showError(String title,Exception ex) {
        JOptionPane.showMessageDialog(this,ex.getMessage(),title,JOptionPane.ERROR_MESSAGE);
    }
    private boolean canEdit() {
        SimulationState state=engine.getState();
        return !closing && (state==SimulationState.READY || state==SimulationState.PAUSED
                || state==SimulationState.TERMINATED);
    }
    private void editBody(BodyItem item) {
        if (!canEdit()) return;
        enterSetup(); setup.put(item.body()); selected=item.body().id(); controls().setupChanged();
    }
    private ArrayList<Body> editableBodies() {
        var bodies=new ArrayList<Body>();
        bodyItems.stream().map(BodyItem::body).sorted(Comparator.comparingInt(Body::id)).forEach(bodies::add);
        return bodies;
    }
    private void enterSetup() {
        invalidateSpecialExperiment();
        setupPreset=Presets.Preset.CUSTOM;
        if (!editingSetup) {
            editingSetup=true; setup=new NBodySetup(editableBodies(),setupParameters);
            history.clear(); energy.clear(); angular.clear();
        }
        controls().markCustom(); controls().setupChanged();
    }
    private void selectBody(int id) {
        selected=id; bodyItems.stream().filter(item -> item.body().id()==id).findFirst()
                .ifPresent(item -> getDefaultLayer().selectItem(item,true));
        if (editingSetup) info.acceptSetup(selectedBody());
    }
    private Body selectedBody() {
        return bodyItems.stream().filter(item -> item.body().id()==selected).map(BodyItem::body).findFirst().orElse(null);
    }
    private NBodyControls controls() { return (NBodyControls)controlPanel; }
    public boolean isEditingSetup() { return editingSetup; }
    public boolean canRunSetup() { return !editingSetup || setup.canRun(); }
    private void updateItemLocks() { updateItemLocks(canEdit()); }
    private void updateItemLocks(boolean editable) {
        boolean deletable=editable;
        for (BodyItem item:bodyItems) item.setEditable(editable,deletable);
        getIContainer().refresh();
    }
    private void settleItemLocks(SimulationContext ctx) {
        updateItemLocks();
        SwingUtilities.invokeLater(() -> {
            if (!closing && ctx==engine.getContext()) updateItemLocks();
        });
    }
    private void trimHistory() { while (history.size() > trailLength) history.removeFirst(); }
    private void accept(Snapshot s) {
        shown = s;
        for (Body body:s.bodies()) bodyItems.stream().filter(item -> item.body().id()==body.id()).findFirst().ifPresent(item -> item.update(body));
        history.addLast(s); trimHistory(); energy.accept(s); angular.accept(s); info.accept(s,selected);
        var d = s.diagnostics();
        String error = Math.abs(initialEnergy) > 1e-14
                ? String.format(java.util.Locale.ROOT,"%+.3e",(d.energy()-initialEnergy)/Math.abs(initialEnergy)) : "undefined (E₀ ≈ 0)";
        diagnosticsLabel.setText(String.format(java.util.Locale.ROOT,
                " t=%.4f   dt=%.3g   ΔE/|E₀|=%s   P=(%.3g, %.3g)   COM=(%.3g, %.3g)",
                s.time(),simulation().parameters().dt(),error,d.px(),d.py(),d.comX(),d.comY()));
        if (perihelionExperiment && s.step()>0) {
            if (perihelionDialog==null || !perihelionDialog.isDisplayable()) {
                perihelionDialog=new PerihelionPlotDialog(SwingUtilities.getWindowAncestor(this));
                perihelionDialog.setVisible(true);
            }
            perihelionDialog.accept(s);
        }
    }
    @Override protected void onSimulationRefresh(SimulationContext ctx) {
        if (closing || editingSetup || shown == null || ctx != engine.getContext()) return;
        Snapshot s = simulation().snapshot();
        if (s.step() != shown.step()) accept(s);
    }
    @Override protected void onSimulationPause(SimulationContext ctx) {
        onSimulationRefresh(ctx); settleItemLocks(ctx);
        if (runAfterReset) { runAfterReset=false; engine.requestRun(); }
    }
    @Override protected void onSimulationDone(SimulationContext ctx) { onSimulationRefresh(ctx); }
    private static Color color(int id) { return Color.getHSBColor((float)((id*.61803398875)%1),.7f,.8f); }
    private void drawBackground(Graphics2D original, IContainer c) {
        if (shown == null) return;
        Graphics2D g = (Graphics2D)original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Point a = new Point(), b = new Point();
            if (trails && !editingSetup) {
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
            if (com && !editingSetup) {
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
                    "When paused, drag bodies or double-click one to edit it; Delete removes selected bodies.",
                    "Changing physics prepares a Custom setup; press Start to begin its new run.",
                    "Use the pointer to select bodies, the navigation tools to change the view, and the camera to save or copy an image.",
                    "Trails and plots sample at the display rate. Velocity arrows show v × 0.4 time units."); }
        };
    }
    @Override public void onStateChange(SimulationContext ctx, SimulationState from, SimulationState to, String reason) {
        if (closing || ctx!=engine.getContext()) return;
        super.onStateChange(ctx,from,to,reason);
        if (ctx!=engine.getContext()) { updateItemLocks(true); return; }
        SimulationContext current=engine.getContext();
        if (to==SimulationState.RUNNING) updateItemLocks(false);
        else if (to==SimulationState.READY || to==SimulationState.PAUSED || to==SimulationState.TERMINATED
                || to==SimulationState.FAILED) settleItemLocks(current);
    }
    @Override public void itemChanged(Layer layer,AItem item,ItemChangeType type) {
        if (rebuildingItems || !(item instanceof BodyItem bodyItem)) return;
        if (type==ItemChangeType.SELECTED) {
            selected=bodyItem.body().id();
            if (editingSetup) info.acceptSetup(bodyItem.body()); else info.accept(shown,selected);
        }
        else if (type==ItemChangeType.MOVED && canEdit()) {
            enterSetup(); setup.put(bodyItem.body()); selected=bodyItem.body().id(); info.acceptSetup(bodyItem.body()); controls().setupChanged();
        }
        else if (type==ItemChangeType.DELETED && canEdit()) {
            enterSetup(); bodyItems.remove(bodyItem); setup.remove(bodyItem.body().id());
            selected=bodyItems.isEmpty() ? -1 : bodyItems.get(0).body().id(); controls().setupChanged(); repaint();
            info.acceptSetup(selectedBody());
        }
    }
    @Override public void prepareForExit() {
        if (closing) return;
        closing=true; invalidateSpecialExperiment(); engine.requestStop(); engine.removeListener(this);
        getDefaultLayer().removeItemChangeListener(this); ((NBodyControls)controlPanel).unbind(); history.clear(); super.prepareForExit();
    }
    @Override public void dispose() { prepareForExit(); super.dispose(); }
}
