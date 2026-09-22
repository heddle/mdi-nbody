package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.graphics.drawable.DrawableAdapter;
import edu.cnu.mdi.graphics.toolbar.ToolBits;
import edu.cnu.mdi.item.AItem;
import edu.cnu.mdi.item.ItemChangeListener;
import edu.cnu.mdi.item.ItemChangeType;
import edu.cnu.mdi.item.Layer;
import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.nbody.model.NBodySetup;
import edu.cnu.mdi.nbody.model.NBodySetupIO;
import edu.cnu.mdi.nbody.model.Parameters;
import edu.cnu.mdi.nbody.model.Presets;
import edu.cnu.mdi.nbody.model.Snapshot;
import edu.cnu.mdi.nbody.sim.NBodySimulation;
import edu.cnu.mdi.sim.SimulationContext;
import edu.cnu.mdi.sim.SimulationEngineConfig;
import edu.cnu.mdi.sim.SimulationState;
import edu.cnu.mdi.sim.ui.SimulationView;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.AbstractViewInfo;

/**
 * Primary world-coordinate view for the N-body application.
 *
 * <p>The numerical model remains worker-owned. This view, its editable
 * {@link BodyItem} objects, trail history, selection, and companion views are
 * all EDT-owned and consume immutable snapshots.</p>
 */
public final class NBodyView extends SimulationView implements ItemChangeListener {
    /** Linked energy plot. */
    private final EnergyPlotView energy;
    /** Linked angular-momentum plot. */
    private final AngularMomentumPlotView angular;
    /** Linked selected-body inspector. */
    private final BodyInfoView info;
    /** EDT-owned display samples used to draw trails. */
    private final ArrayDeque<Snapshot> history = new ArrayDeque<>();
    /** MDI item corresponding to each currently displayed body. */
    private final List<BodyItem> bodyItems = new ArrayList<>();
    /** Snapshot currently represented by the items and companion views. */
    private Snapshot shown;
    /** Stable ID of the selected body, or {@code -1}. */
    private int selected;
    /** Maximum number of display snapshots retained for trails. */
    private int trailLength = 500;
    /** Requested simulation refresh rate. */
    private int updateHz = 30;
    /** Selection to restore after an asynchronous engine replacement. */
    private Integer pendingSelection;
    /** Whether trail rendering is enabled. */
    private boolean trails = true;
    /** Whether velocity-vector rendering is enabled. */
    private boolean velocities;
    /** Whether center-of-mass rendering is enabled. */
    private boolean com = true;
    /** Prevents callbacks from doing work during disposal. */
    private boolean closing;
    /** Suppresses item-change callbacks during a bulk item rebuild. */
    private boolean rebuildingItems;
    /** Total energy at step zero, used by the footer diagnostic. */
    private double initialEnergy;
    /** Parameters represented by the current setup or run. */
    private Parameters setupParameters = Parameters.defaults();
    /** True only while the pristine Mercury/Jupiter experiment is active. */
    private boolean perihelionExperiment;
    /** Non-modal special-purpose perihelion diagnostic. */
    private PerihelionPlotDialog perihelionDialog;
    /** Mutable setup currently represented by the canvas. */
    private NBodySetup setup;
    /** Exact initial conditions captured when the current run began. */
    private NBodySetup initialSetup;
    /** Named source of the current setup, or {@link Presets.Preset#CUSTOM}. */
    private Presets.Preset setupPreset = Presets.Preset.CIRCULAR;
    /** Whether the canvas represents a prepared setup rather than a run. */
    private boolean editingSetup;
    /** Whether the next canvas click should place a body. */
    private boolean placingBody;
    /** Whether an engine replacement should transition directly into running. */
    private boolean runAfterReset;
    /** Footer containing active numerical diagnostics or setup guidance. */
    private final JLabel diagnosticsLabel = new JLabel();

    /**
     * Creates the simulation view and connects its three companion views.
     *
     * @param energy linked energy plot
     * @param angular linked angular-momentum plot
     * @param info linked selected-body inspector
     */
    public NBodyView(EnergyPlotView energy, AngularMomentumPlotView angular, BodyInfoView info) {
        super(new NBodySimulation(
                        Presets.create(Presets.Preset.CIRCULAR, Parameters.defaults(), 20, 42),
                        Parameters.defaults()),
                new SimulationEngineConfig(0, 0, 0, true),
                true,
                NBodyControls::new,
                PropertyUtils.TITLE, "N-body simulation",
                PropertyUtils.WIDTH, 850,
                PropertyUtils.HEIGHT, 650,
                PropertyUtils.WORLDSYSTEM, new Rectangle2D.Double(-5, -4, 10, 8),
                PropertyUtils.VISIBLE, true,
                PropertyUtils.TOOLBARBITS,
                ToolBits.POINTER | ToolBits.PAN | ToolBits.BOXZOOM | ToolBits.ZOOMIN
                        | ToolBits.ZOOMOUT | ToolBits.RESETZOOM | ToolBits.UNDOZOOM
                        | ToolBits.CAMERA | ToolBits.INFO);
        this.energy = energy;
        this.angular = angular;
        this.info = info;

        JPanel south = new JPanel(new BorderLayout());
        var controlsScroll = new JScrollPane(controlPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        controlsScroll.setBorder(null);
        controlsScroll.setPreferredSize(new Dimension(
                0, controlPanel.getPreferredSize().height + 20));
        south.add(controlsScroll, BorderLayout.CENTER);
        south.add(diagnosticsLabel, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        simulation().bind(engine);
        getDefaultLayer().addItemChangeListener(this);
        initializeSnapshot();
        getIContainer().setBeforeDraw(new DrawableAdapter() {
            @Override
            public void draw(Graphics2D graphics, IContainer container) {
                drawBackground(graphics, container);
            }
        });
        getIContainer().getComponent().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                equalAxes();
            }
        });
        getIContainer().getComponent().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!placingBody || !SwingUtilities.isLeftMouseButton(event)
                        || event.getClickCount() != 1 || !canEdit()) {
                    return;
                }
                placingBody = false;
                var point = new java.awt.geom.Point2D.Double();
                getIContainer().localToWorld(event.getPoint(), point);
                enterSetup();
                Body body = new Body(setup.nextId(), 1, point.x, point.y, 0, 0);
                setup.put(body);
                rebuildBodyItems(setup.bodies());
                selectBody(body.id());
                controls().setupChanged();
            }
        });
        startSimulation();
    }

    private void equalAxes() {
        IContainer container = getIContainer();
        Dimension size = container.getComponent().getSize();
        if (size.width <= 0 || size.height <= 0) {
            return;
        }
        Rectangle2D.Double world = container.getWorldSystem();
        double width = world.height * size.width / size.height;
        container.setWorldSystem(new Rectangle2D.Double(
                world.getCenterX() - width / 2, world.y, width, world.height));
    }

    private NBodySimulation simulation() {
        return (NBodySimulation) engine.getSimulation();
    }

    private void initializeSnapshot() {
        shown = simulation().snapshot();
        initialEnergy = shown.diagnostics().energy();
        history.clear();
        setup = new NBodySetup(shown.bodies(), setupParameters);
        initialSetup = new NBodySetup(shown.bodies(), setupParameters);
        editingSetup = false;
        energy.clear();
        angular.clear();
        selected = pendingSelection != null
                && shown.bodies().stream().anyMatch(body -> body.id() == pendingSelection)
                        ? pendingSelection
                        : shown.bodies().get(0).id();
        pendingSelection = null;
        rebuildBodyItems(shown.bodies());
        accept(shown);
        bodyItems.stream()
                .filter(item -> item.body().id() == selected)
                .findFirst()
                .ifPresent(item -> getDefaultLayer().selectItem(item, true));
    }

    private void rebuildBodyItems(List<Body> bodies) {
        rebuildingItems = true;
        try {
            for (BodyItem item : List.copyOf(bodyItems)) {
                getDefaultLayer().remove(item);
            }
            bodyItems.clear();
            for (Body body : bodies) {
                BodyItem item = new BodyItem(getDefaultLayer(), body, this::editBody);
                item.setShowVelocity(velocities);
                bodyItems.add(item);
            }
        } finally {
            rebuildingItems = false;
        }
        updateItemLocks();
    }
    /**
     * Replaces the engine with a paused run at the supplied initial conditions.
     *
     * @param initial initial body states
     * @param parameters fixed parameters for the replacement run
     */
    public void reset(List<Body> initial, Parameters parameters) {
        if (closing) {
            return;
        }
        setupParameters = parameters;
        placingBody = false;
        requestEngineReset(() -> new NBodySimulation(initial, parameters), next -> {
            simulation().bind(next);
            simulation().setUpdateHz(updateHz);
            initializeSnapshot();
            double extent = initial.stream()
                    .mapToDouble(body -> Math.max(Math.abs(body.x()), Math.abs(body.y())))
                    .max()
                    .orElse(2);
            extent = Math.max(2, extent * 1.4);
            getIContainer().resetWorldSystem(
                    new Rectangle2D.Double(-extent, -extent, 2 * extent, 2 * extent));
            equalAxes();
            getIContainer().resetWorldSystem(getIContainer().getWorldSystem());
            updateItemLocks(true);
        }, true, true);
    }
    /**
     * Replaces the current run and records the named preset that produced it.
     *
     * @param preset preset identity
     * @param initial generated body states
     * @param parameters fixed parameters for the run
     */
    public void resetPreset(Presets.Preset preset, List<Body> initial, Parameters parameters) {
        invalidateSpecialExperiment();
        setupPreset = preset;
        perihelionExperiment = preset == Presets.Preset.MERCURY_JUPITER
                && parameters.equals(Presets.mercuryPrecessionParameters());
        reset(initial, parameters);
    }

    /** Restores the exact initial conditions of the current run at time zero. */
    public void restoreCurrentModel() {
        if (!canEdit() || initialSetup == null) {
            return;
        }
        invalidateSpecialExperiment();
        perihelionExperiment = setupPreset == Presets.Preset.MERCURY_JUPITER
                && initialSetup.parameters().equals(Presets.mercuryPrecessionParameters());
        controls().setParameters(initialSetup.parameters());
        reset(initialSetup.bodies(), initialSetup.parameters());
    }
    /**
     * Places a named preset on the canvas as an editable, not-yet-running setup.
     *
     * @param preset named preset
     * @param bodies generated initial bodies
     * @param parameters prepared run parameters
     */
    public void loadPreset(Presets.Preset preset, List<Body> bodies, Parameters parameters) {
        if (!canEdit() || preset == Presets.Preset.CUSTOM) {
            return;
        }
        invalidateSpecialExperiment();
        controls().setParameters(parameters);
        setupPreset = preset;
        setup = new NBodySetup(bodies, parameters);
        setupParameters = parameters;
        editingSetup = true;
        placingBody = false;
        history.clear();
        energy.clear();
        angular.clear();
        rebuildBodyItems(setup.bodies());
        selected = setup.bodies().get(0).id();
        selectBody(selected);
        diagnosticsLabel.setText(" Preset loaded. Edit it or press Start.");
        controls().setupChanged();
        repaint();
    }
    /** Closes and forgets diagnostics that apply only to an unmodified preset. */
    public void invalidateSpecialExperiment() {
        perihelionExperiment = false;
        if (perihelionDialog != null) {
            perihelionDialog.dispose();
            perihelionDialog = null;
        }
    }
    /** Advances a ready or paused run by exactly one integration step. */
    public void singleStep() {
        if (editingSetup) {
            return;
        }
        SimulationState state = engine.getState();
        if (state == SimulationState.READY || state == SimulationState.PAUSED) {
            simulation().requestSingleStep();
        }
    }
    /**
     * Sets the requested display refresh rate.
     *
     * @param hz refresh requests per second
     */
    public void setUpdateHz(int hz) {
        updateHz = hz;
        simulation().setUpdateHz(hz);
    }

    /**
     * Applies display-only settings without changing the numerical model.
     *
     * @param trails whether trails are visible
     * @param velocities whether velocity arrows are visible
     * @param com whether the center-of-mass marker is visible
     * @param length maximum retained trail samples
     */
    public void setDisplay(boolean trails, boolean velocities, boolean com, int length) {
        this.trails = trails;
        this.velocities = velocities;
        this.com = com;
        trailLength = length;
        bodyItems.forEach(item -> item.setShowVelocity(velocities));
        trimHistory();
        repaint();
    }
    /** Converts changed physics controls into a prepared custom setup. */
    public void physicsChanged() {
        if (!canEdit()) {
            return;
        }
        try {
            enterSetup();
            setup.setParameters(controls().parameters());
            setupParameters = setup.parameters();
            diagnosticsLabel.setText(" Physics changed. Press Start to begin the custom setup.");
        } catch (IllegalArgumentException ex) {
            showError("Invalid settings", ex);
        }
    }
    /** Arms one-shot canvas placement for a new unit-mass body. */
    public void addBody() {
        if (!canEdit() || bodyItems.size() >= NBodySetup.MAX_BODIES) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        enterSetup();
        placingBody = true;
        diagnosticsLabel.setText(" Click the canvas to place the new body.");
    }
    /** Clears the canvas and starts a new, initially empty custom setup. */
    public void newModel() {
        if (!canEdit()) {
            return;
        }
        Parameters parameters;
        try {
            parameters = controls().parameters();
        } catch (IllegalArgumentException ex) {
            showError("Invalid settings", ex);
            return;
        }
        invalidateSpecialExperiment();
        setupPreset = Presets.Preset.CUSTOM;
        editingSetup = true;
        placingBody = false;
        setup = NBodySetup.empty(parameters);
        history.clear();
        energy.clear();
        angular.clear();
        rebuildBodyItems(List.of());
        selected = -1;
        info.acceptSetup(null);
        diagnosticsLabel.setText(" Custom setup: click Add Body, then click the canvas.");
        controls().markCustom();
        controls().setupChanged();
        repaint();
    }
    /** Starts a prepared setup, or starts an unchanged run at step zero. */
    public void startOrRun() {
        if (!editingSetup) {
            runSimulation();
            return;
        }
        if (!setup.canRun()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        try {
            setup.setParameters(controls().parameters());
        } catch (IllegalArgumentException ex) {
            showError("Invalid settings", ex);
            return;
        }
        invalidateSpecialExperiment();
        perihelionExperiment = setupPreset == Presets.Preset.MERCURY_JUPITER
                && setup.parameters().equals(Presets.mercuryPrecessionParameters());
        runAfterReset = true;
        reset(setup.bodies(), setup.parameters());
    }
    /** Opens a chooser and saves the current setup or run as versioned JSON. */
    public void saveModel() {
        NBodySetup current = editingSetup
                ? setup
                : new NBodySetup(editableBodies(), setupParameters);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("nbody-model.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            NBodySetupIO.write(chooser.getSelectedFile().toPath(), current);
        } catch (IOException ex) {
            showError("Could not save model", ex);
        }
    }
    /** Opens a chooser and loads a JSON model as an editable custom setup. */
    public void openModel() {
        if (!canEdit()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            invalidateSpecialExperiment();
            setup = NBodySetupIO.read(chooser.getSelectedFile().toPath());
            setupParameters = setup.parameters();
            setupPreset = Presets.Preset.CUSTOM;
            editingSetup = true;
            placingBody = false;
            rebuildBodyItems(setup.bodies());
            history.clear();
            energy.clear();
            angular.clear();
            selected = setup.bodies().isEmpty() ? -1 : setup.bodies().get(0).id();
            info.acceptSetup(selectedBody());
            controls().setParameters(setup.parameters());
            controls().markCustom();
            controls().setupChanged();
            repaint();
        } catch (IOException ex) {
            showError("Could not open model", ex);
        }
    }
    /** Displays the menu of common initial-condition transformations. */
    public void showSetupTools() {
        if (!canEdit()) {
            return;
        }
        enterSetup();
        String[] choices = {
                "Center COM at origin",
                "Remove net momentum",
                "Duplicate selected body",
                "Circular velocity around another body",
                "Reverse all velocities",
                "Scale positions",
                "Scale velocities"
        };
        String choice = (String) JOptionPane.showInputDialog(this,
                "Choose an operation:", "Setup Tools", JOptionPane.PLAIN_MESSAGE,
                null, choices, choices[0]);
        if (choice == null) {
            return;
        }
        try {
            switch (choice) {
                case "Center COM at origin" -> setup.centerOnMass();
                case "Remove net momentum" -> setup.removeNetMomentum();
                case "Duplicate selected body" -> {
                    if (selected < 0) {
                        throw new IllegalArgumentException("Select a body first");
                    }
                    setup.duplicate(selected);
                }
                case "Circular velocity around another body" -> circularVelocity();
                case "Reverse all velocities" -> setup.reverseVelocities();
                case "Scale positions" -> setup.scalePositions(promptScale("Position scale"));
                case "Scale velocities" -> setup.scaleVelocities(promptScale("Velocity scale"));
            }
            rebuildBodyItems(setup.bodies());
            controls().setupChanged();
            repaint();
        } catch (IllegalArgumentException ex) {
            showError("Cannot apply setup operation", ex);
        }
    }

    private void circularVelocity() {
        if (selected < 0) {
            throw new IllegalArgumentException("Select the orbiting body first");
        }
        String input = JOptionPane.showInputDialog(this, "Primary body ID:");
        if (input == null) {
            throw new IllegalArgumentException("Operation cancelled");
        }
        setup.setCircularVelocity(selected, Integer.parseInt(input.trim()));
    }

    private double promptScale(String title) {
        String input = JOptionPane.showInputDialog(this, title + ":", "1.0");
        if (input == null) {
            throw new IllegalArgumentException("Operation cancelled");
        }
        return Double.parseDouble(input.trim());
    }

    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(
                this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private boolean canEdit() {
        SimulationState state = engine.getState();
        return !closing
                && (state == SimulationState.READY
                        || state == SimulationState.PAUSED
                        || state == SimulationState.TERMINATED);
    }

    private void editBody(BodyItem item) {
        if (!canEdit()) {
            return;
        }
        enterSetup();
        setup.put(item.body());
        selected = item.body().id();
        controls().setupChanged();
    }

    private ArrayList<Body> editableBodies() {
        var bodies = new ArrayList<Body>();
        bodyItems.stream()
                .map(BodyItem::body)
                .sorted(Comparator.comparingInt(Body::id))
                .forEach(bodies::add);
        return bodies;
    }

    private void enterSetup() {
        invalidateSpecialExperiment();
        setupPreset = Presets.Preset.CUSTOM;
        if (!editingSetup) {
            editingSetup = true;
            setup = new NBodySetup(editableBodies(), setupParameters);
            history.clear();
            energy.clear();
            angular.clear();
        }
        controls().markCustom();
        controls().setupChanged();
    }

    private void selectBody(int id) {
        selected = id;
        bodyItems.stream()
                .filter(item -> item.body().id() == id)
                .findFirst()
                .ifPresent(item -> getDefaultLayer().selectItem(item, true));
        if (editingSetup) {
            info.acceptSetup(selectedBody());
        }
    }

    private Body selectedBody() {
        return bodyItems.stream()
                .filter(item -> item.body().id() == selected)
                .map(BodyItem::body)
                .findFirst()
                .orElse(null);
    }

    private NBodyControls controls() {
        return (NBodyControls) controlPanel;
    }

    /**
     * Reports whether the canvas currently represents editable initial conditions.
     *
     * @return {@code true} in setup mode
     */
    public boolean isEditingSetup() {
        return editingSetup;
    }

    /**
     * Reports whether Start has enough bodies to create a numerical model.
     *
     * @return {@code true} outside setup mode or for a valid setup body count
     */
    public boolean canRunSetup() {
        return !editingSetup || setup.canRun();
    }

    private void updateItemLocks() {
        updateItemLocks(canEdit());
    }

    private void updateItemLocks(boolean editable) {
        for (BodyItem item : bodyItems) {
            item.setEditable(editable, editable);
        }
        getIContainer().refresh();
    }

    private void settleItemLocks(SimulationContext context) {
        updateItemLocks();
        SwingUtilities.invokeLater(() -> {
            if (!closing && context == engine.getContext()) {
                updateItemLocks();
            }
        });
    }

    private void trimHistory() {
        while (history.size() > trailLength) {
            history.removeFirst();
        }
    }

    private void accept(Snapshot snapshot) {
        shown = snapshot;
        for (Body body : snapshot.bodies()) {
            bodyItems.stream()
                    .filter(item -> item.body().id() == body.id())
                    .findFirst()
                    .ifPresent(item -> item.update(body));
        }

        history.addLast(snapshot);
        trimHistory();
        energy.accept(snapshot);
        angular.accept(snapshot);
        info.accept(snapshot, selected);

        Snapshot.Diagnostics diagnostics = snapshot.diagnostics();
        String error = Math.abs(initialEnergy) > 1e-14
                ? String.format(Locale.ROOT, "%+.3e",
                        (diagnostics.energy() - initialEnergy) / Math.abs(initialEnergy))
                : "undefined (E₀ ≈ 0)";
        diagnosticsLabel.setText(String.format(Locale.ROOT,
                " t=%.4f   dt=%.3g   ΔE/|E₀|=%s   P=(%.3g, %.3g)   COM=(%.3g, %.3g)",
                snapshot.time(), simulation().parameters().dt(), error,
                diagnostics.px(), diagnostics.py(), diagnostics.comX(), diagnostics.comY()));

        if (perihelionExperiment && snapshot.step() > 0) {
            if (perihelionDialog == null || !perihelionDialog.isDisplayable()) {
                perihelionDialog = new PerihelionPlotDialog(
                        SwingUtilities.getWindowAncestor(this));
                perihelionDialog.setVisible(true);
            }
            perihelionDialog.accept(snapshot);
        }
    }

    @Override
    protected void onSimulationRefresh(SimulationContext context) {
        if (closing || editingSetup || shown == null || context != engine.getContext()) {
            return;
        }
        Snapshot snapshot = simulation().snapshot();
        if (snapshot.step() != shown.step()) {
            accept(snapshot);
        }
    }

    @Override
    protected void onSimulationPause(SimulationContext context) {
        onSimulationRefresh(context);
        settleItemLocks(context);
        if (runAfterReset) {
            runAfterReset = false;
            engine.requestRun();
        }
    }

    @Override
    protected void onSimulationDone(SimulationContext context) {
        onSimulationRefresh(context);
    }

    private static Color color(int id) {
        return Color.getHSBColor((float) ((id * 0.61803398875) % 1), 0.7f, 0.8f);
    }

    private void drawBackground(Graphics2D original, IContainer container) {
        if (shown == null) {
            return;
        }
        Graphics2D graphics = (Graphics2D) original.create();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Point start = new Point();
            Point end = new Point();
            if (trails && !editingSetup) {
                Snapshot previous = null;
                for (Snapshot snapshot : history) {
                    if (previous != null) {
                        for (int index = 0; index < snapshot.bodies().size(); index++) {
                            Body oldBody = previous.bodies().get(index);
                            Body newBody = snapshot.bodies().get(index);
                            container.worldToLocal(start, oldBody.x(), oldBody.y());
                            container.worldToLocal(end, newBody.x(), newBody.y());
                            Color bodyColor = color(newBody.id());
                            graphics.setColor(new Color(
                                    bodyColor.getRed(), bodyColor.getGreen(),
                                    bodyColor.getBlue(), 110));
                            graphics.drawLine(start.x, start.y, end.x, end.y);
                        }
                    }
                    previous = snapshot;
                }
            }
            if (com && !editingSetup) {
                container.worldToLocal(
                        start, shown.diagnostics().comX(), shown.diagnostics().comY());
                graphics.setColor(Color.DARK_GRAY);
                graphics.drawLine(start.x - 8, start.y, start.x + 8, start.y);
                graphics.drawLine(start.x, start.y - 8, start.x, start.y + 8);
                graphics.drawString("COM", start.x + 10, start.y - 5);
            }
        } finally {
            graphics.dispose();
        }
    }

    @Override
    public AbstractViewInfo getViewInfo() {
        return new AbstractViewInfo() {
            @Override
            public String getTitle() {
                return "N-body simulation";
            }

            @Override
            public String getPurpose() {
                return "Explore softened Newtonian gravity in dimensionless units.";
            }

            @Override
            public List<String> getUsageBullets() {
                return List.of(
                        "Start, pause, resume, or single-step the simulation.",
                        "When paused, drag bodies or double-click one to edit it; "
                                + "Delete removes selected bodies.",
                        "Changing physics prepares a Custom setup; press Start to begin its "
                                + "new run.",
                        "Use the pointer to select bodies, the navigation tools to change "
                                + "the view, and the camera to save or copy an image.",
                        "Trails and plots sample at the display rate. Velocity arrows show "
                                + "v × 0.4 time units.");
            }
        };
    }

    @Override
    public void onStateChange(SimulationContext context, SimulationState from,
            SimulationState to, String reason) {
        if (closing || context != engine.getContext()) {
            return;
        }
        super.onStateChange(context, from, to, reason);

        // The superclass may replace the engine while processing a terminal
        // callback. In that case, unlock the new setup and ignore the old context.
        if (context != engine.getContext()) {
            updateItemLocks(true);
            return;
        }

        SimulationContext current = engine.getContext();
        if (to == SimulationState.RUNNING) {
            updateItemLocks(false);
        } else if (to == SimulationState.READY
                || to == SimulationState.PAUSED
                || to == SimulationState.TERMINATED
                || to == SimulationState.FAILED) {
            settleItemLocks(current);
        }
    }

    @Override
    public void itemChanged(Layer layer, AItem item, ItemChangeType type) {
        if (rebuildingItems || !(item instanceof BodyItem bodyItem)) {
            return;
        }

        if (type == ItemChangeType.SELECTED) {
            selected = bodyItem.body().id();
            if (editingSetup) {
                info.acceptSetup(bodyItem.body());
            } else {
                info.accept(shown, selected);
            }
        } else if (type == ItemChangeType.MOVED && canEdit()) {
            enterSetup();
            setup.put(bodyItem.body());
            selected = bodyItem.body().id();
            info.acceptSetup(bodyItem.body());
            controls().setupChanged();
        } else if (type == ItemChangeType.DELETED && canEdit()) {
            enterSetup();
            bodyItems.remove(bodyItem);
            setup.remove(bodyItem.body().id());
            selected = bodyItems.isEmpty() ? -1 : bodyItems.get(0).body().id();
            controls().setupChanged();
            repaint();
            info.acceptSetup(selectedBody());
        }
    }

    @Override
    public void prepareForExit() {
        if (closing) {
            return;
        }
        closing = true;
        invalidateSpecialExperiment();
        engine.requestStop();
        engine.removeListener(this);
        getDefaultLayer().removeItemChangeListener(this);
        controls().unbind();
        history.clear();
        super.prepareForExit();
    }

    @Override
    public void dispose() {
        prepareForExit();
        super.dispose();
    }
}
