package edu.cnu.mdi.nbody.view;

import java.awt.*;
import javax.swing.*;
import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.sim.*;
import edu.cnu.mdi.sim.ui.ISimulationControlPanel;

/** Uses MDI's host/listener binding contract and engine lifecycle. */
public final class NBodyControls extends JPanel implements ISimulationControlPanel, SimulationListener {
    private NBodyView host;
    private SimulationEngine boundEngine;
    private final JButton start = new JButton("Start"), pause = new JButton("Pause"), resume = new JButton("Resume"),
            stop = new JButton("Stop"), reset = new JButton("Reset"), step = new JButton("Single Step");
    private final JComboBox<Presets.Preset> preset = new JComboBox<>(Presets.Preset.values());
    private final JComboBox<Parameters.Integrator> integrator = new JComboBox<>(Parameters.Integrator.values());
    private final JSpinner g = number(1,.001,100,.1), epsilon = number(.01,.000001,1,.001),
            dt = number(.002,.000001,.1,.001), hz = new JSpinner(new SpinnerNumberModel(30,1,60,1)),
            count = new JSpinner(new SpinnerNumberModel(20,2,50,1)),
            seed = new JSpinner(new SpinnerNumberModel(42L,Long.MIN_VALUE,Long.MAX_VALUE,1L)),
            trailLength = new JSpinner(new SpinnerNumberModel(500,0,5000,50));
    private final JLabel status = new JLabel("Ready");
    private final JCheckBox trails = new JCheckBox("Trails",true), velocities = new JCheckBox("Velocity vectors"), com = new JCheckBox("COM",true);

    public NBodyControls() {
        super(new GridLayout(0,1,0,2));
        JPanel buttons = row();
        for (JButton b : new JButton[]{start,pause,resume,stop,reset,step}) buttons.add(b);
        buttons.add(status); add(buttons);
        JPanel physics = row();
        field(physics,"Preset",preset); field(physics,"Integrator",integrator); add(physics);
        JPanel numbers = row();
        field(numbers,"G",g); field(numbers,"ε",epsilon); field(numbers,"dt",dt);
        field(numbers,"Cluster N",count); field(numbers,"Seed",seed); add(numbers);
        JPanel display = row();
        field(display,"Updates/s",hz); field(display,"Trail samples",trailLength);
        display.add(trails); display.add(velocities); display.add(com); add(display);
        add(new JLabel("Physics, preset, N and seed apply on Reset. Display settings apply immediately."));
        ((JSpinner.DefaultEditor)seed.getEditor()).getTextField().setColumns(8);
        start.addActionListener(e -> host.runSimulation());
        pause.addActionListener(e -> host.pauseSimulation());
        resume.addActionListener(e -> host.resumeSimulation());
        stop.addActionListener(e -> host.stopSimulation());
        step.addActionListener(e -> { step.setEnabled(false); start.setEnabled(false); resume.setEnabled(false); host.singleStep(); });
        reset.addActionListener(e -> reset());
        hz.addChangeListener(e -> { if (host != null) host.setUpdateHz(((Number)hz.getValue()).intValue()); });
        trailLength.addChangeListener(e -> displayChanged());
        for (JCheckBox box : new JCheckBox[]{trails,velocities,com}) box.addActionListener(e -> displayChanged());
    }
    private static JSpinner number(double value,double min,double max,double step) {
        return new JSpinner(new SpinnerNumberModel(value,min,max,step));
    }
    private static JPanel row() { return new JPanel(new FlowLayout(FlowLayout.LEFT,6,1)); }
    private static void field(JPanel row, String label, JComponent component) { row.add(new JLabel(label)); row.add(component); }
    private void displayChanged() {
        if (host != null) host.setDisplay(trails.isSelected(),velocities.isSelected(),com.isSelected(), ((Number)trailLength.getValue()).intValue());
    }
    private void reset() {
        try {
            for (JSpinner spinner : new JSpinner[]{g,epsilon,dt,count,seed,hz,trailLength}) spinner.commitEdit();
            Parameters p = new Parameters(((Number)g.getValue()).doubleValue(), ((Number)epsilon.getValue()).doubleValue(),
                    ((Number)dt.getValue()).doubleValue(), (Parameters.Integrator)integrator.getSelectedItem());
            host.reset(Presets.create((Presets.Preset)preset.getSelectedItem(),p,((Number)count.getValue()).intValue(),
                    ((Number)seed.getValue()).longValue()),p);
        } catch (java.text.ParseException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid numeric values: " + ex.getMessage(), "Invalid settings", JOptionPane.ERROR_MESSAGE);
        }
    }
    @Override public void bind(ISimulationHost host) {
        unbind(); this.host = (NBodyView)host;
        boundEngine = host.getSimulationEngine(); boundEngine.addListener(this); applyState();
    }
    @Override public void unbind() {
        if (boundEngine != null) boundEngine.removeListener(this);
        boundEngine = null; host = null;
    }
    @Override public void onStateChange(SimulationContext ctx, SimulationState from, SimulationState to, String reason) {
        if (boundEngine != null && ctx == boundEngine.getContext()) applyState();
    }
    @Override public void onFail(SimulationContext ctx, Throwable error) { status.setText("Failed: " + error.getMessage()); }
    private void applyState() {
        SimulationState s = boundEngine.getState();
        boolean idle = s == SimulationState.READY || s == SimulationState.PAUSED;
        boolean initial = ((edu.cnu.mdi.nbody.sim.NBodySimulation)boundEngine.getSimulation()).snapshot().step() == 0;
        start.setEnabled(idle && initial); resume.setEnabled(s == SimulationState.PAUSED && !initial);
        pause.setEnabled(s == SimulationState.RUNNING); step.setEnabled(idle);
        stop.setEnabled(idle || s == SimulationState.RUNNING);
        reset.setEnabled(s != SimulationState.INITIALIZING && s != SimulationState.TERMINATING);
        status.setText(s.toString());
    }
}
