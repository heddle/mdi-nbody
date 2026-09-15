package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import edu.cnu.mdi.nbody.model.*;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.BaseView;

public final class BodyInfoView extends BaseView {
    private final DefaultTableModel table = new DefaultTableModel(new Object[]{"Property", "Value"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    public BodyInfoView() {
        super(PropertyUtils.TITLE, "Selected body", PropertyUtils.USECONTAINER, false,
                PropertyUtils.WIDTH, 400, PropertyUtils.HEIGHT, 300, PropertyUtils.VISIBLE, true);
        add(new JScrollPane(new JTable(table)), BorderLayout.CENTER);
    }
    public void accept(Snapshot s, int selected) {
        table.setRowCount(0);
        Body b = s.bodies().stream().filter(body -> body.id() == selected).findFirst().orElse(null);
        if (b == null) { table.addRow(new Object[]{"Selection", "Click a body with the pointer tool"}); return; }
        table.addRow(new Object[]{"Body", b.id()});
        row("Mass", b.mass()); row("x", b.x()); row("y", b.y()); row("vx", b.vx()); row("vy", b.vy());
        row("Speed", b.speed()); row("Kinetic energy", b.kineticEnergy());
        row("Distance from COM", Math.hypot(b.x()-s.diagnostics().comX(), b.y()-s.diagnostics().comY()));
    }
    public void acceptSetup(Body body) {
        table.setRowCount(0);
        if (body==null) { table.addRow(new Object[]{"Setup", "Add or select a body"}); return; }
        table.addRow(new Object[]{"Body",body.id()});
        row("Mass",body.mass()); row("x",body.x()); row("y",body.y()); row("vx",body.vx()); row("vy",body.vy());
        row("Speed",body.speed()); row("Kinetic energy",body.kineticEnergy());
    }
    private void row(String name, double value) { table.addRow(new Object[]{name, String.format(java.util.Locale.ROOT,"%.8g", value)}); }
}
