package edu.cnu.mdi.nbody.view;

import java.awt.BorderLayout;
import java.util.Locale;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.nbody.model.Snapshot;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.BaseView;

/** Displays scalar properties for the body selected in the simulation view. */
public final class BodyInfoView extends BaseView {

    /** Non-editable two-column table model rendered by this view. */
    private final DefaultTableModel table = new DefaultTableModel(
            new Object[] {"Property", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    /** Creates the read-only selected-body table. */
    public BodyInfoView() {
        super(PropertyUtils.TITLE, "Selected body",
                PropertyUtils.USECONTAINER, false,
                PropertyUtils.WIDTH, 400,
                PropertyUtils.HEIGHT, 300,
                PropertyUtils.VISIBLE, true);
        add(new JScrollPane(new JTable(table)), BorderLayout.CENTER);
    }

    /**
     * Displays a selected body from a running-state snapshot.
     *
     * @param snapshot immutable simulation state
     * @param selected selected body ID
     */
    public void accept(Snapshot snapshot, int selected) {
        table.setRowCount(0);
        Body body = snapshot.bodies().stream()
                .filter(candidate -> candidate.id() == selected)
                .findFirst()
                .orElse(null);
        if (body == null) {
            table.addRow(new Object[] {"Selection", "Click a body with the pointer tool"});
            return;
        }

        addBodyRows(body);
        row("Distance from COM", Math.hypot(
                body.x() - snapshot.diagnostics().comX(),
                body.y() - snapshot.diagnostics().comY()));
    }

    /**
     * Displays a body from an editable setup, where run-level diagnostics such
     * as center of mass are intentionally unavailable.
     *
     * @param body selected setup body, or {@code null} when nothing is selected
     */
    public void acceptSetup(Body body) {
        table.setRowCount(0);
        if (body == null) {
            table.addRow(new Object[] {"Setup", "Add or select a body"});
            return;
        }
        addBodyRows(body);
    }

    private void addBodyRows(Body body) {
        table.addRow(new Object[] {"Body", body.id()});
        row("Mass", body.mass());
        row("x", body.x());
        row("y", body.y());
        row("vx", body.vx());
        row("vy", body.vy());
        row("Speed", body.speed());
        row("Kinetic energy", body.kineticEnergy());
    }

    private void row(String name, double value) {
        table.addRow(new Object[] {name, String.format(Locale.ROOT, "%.8g", value)});
    }
}
