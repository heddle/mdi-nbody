package edu.cnu.mdi.nbody.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.item.ItemModification;
import edu.cnu.mdi.item.Layer;
import edu.cnu.mdi.item.PointItem;
import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.util.SmartDoubleFormatter;

/**
 * EDT-owned interactive representation of one immutable body state.
 *
 * <p>The MDI item owns only presentation and editing state. While a simulation
 * runs, snapshots update the item. While paused or in setup mode, the item can
 * publish edited body records back to {@link NBodyView}.</p>
 */
final class BodyItem extends PointItem {

    private static final double VELOCITY_ARROW_SCALE = 0.4;

    /** Callback invoked after the numeric editor accepts a body. */
    @FunctionalInterface
    interface Editor {
        void edit(BodyItem item);
    }

    private Body body;
    private final Editor editor;
    private boolean showVelocity;
    private boolean editable;
    private boolean editingVelocity;

    BodyItem(Layer layer, Body body, Editor editor) {
        super(layer, new Point2D.Double(body.x(), body.y()));
        this.body = body;
        this.editor = editor;
        setDisplayName("Body " + body.id());
        setDraggable(false);
        setDeletable(false);
        setResizable(false);
        setLocked(false);
        setDoubleClickable(true);
        setStyleEditable(false);
    }

    Body body() {
        Point2D.Double position = getFocus();
        // MDI clears focus before delivering a deletion notification. Retain the
        // last complete record so the listener can still identify the body.
        if (position == null) {
            return body;
        }
        return new Body(body.id(), body.mass(), position.x, position.y, body.vx(), body.vy());
    }

    void update(Body next) {
        body = next;
        setFocus(new Point2D.Double(next.x(), next.y()));
        setDirty(true);
    }

    void setShowVelocity(boolean show) {
        showVelocity = show;
        setDirty(true);
    }

    void setEditable(boolean editable, boolean deletable) {
        this.editable = editable;
        // Engine replacement can briefly traverse NEW/INITIALIZING. Reassert the
        // item invariants whenever the settled run state is applied.
        setLocked(false);
        setSelectable(true);
        setDoubleClickable(editable);
        setDraggable(editable);
        setDeletable(editable && deletable);
    }

    private int radius() {
        return (int) Math.max(4, Math.min(14, 7 * Math.cbrt(body.mass())));
    }

    private Color color() {
        return Color.getHSBColor((float) ((body.id() * 0.61803398875) % 1), 0.7f, 0.8f);
    }

    @Override
    public Rectangle getBounds(IContainer container) {
        Point center = getFocusPoint(container);
        int extent = radius() + 4;
        Rectangle bounds = new Rectangle(
                center.x - extent, center.y - extent, 2 * extent, 2 * extent);
        if ((showVelocity || editable && isSelected()) && hasVelocityHandle(container)) {
            bounds.add(velocityPoint(container));
        }
        return bounds;
    }

    private Point velocityPoint(IContainer container) {
        Body current = body();
        Point point = new Point();
        container.worldToLocal(point,
                current.x() + current.vx() * VELOCITY_ARROW_SCALE,
                current.y() + current.vy() * VELOCITY_ARROW_SCALE);
        return point;
    }

    private boolean hasVelocityHandle(IContainer container) {
        return getFocusPoint(container).distance(velocityPoint(container)) > radius() + 4;
    }

    @Override
    public void drawItem(Graphics2D original, IContainer container) {
        Graphics2D graphics = (Graphics2D) original.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Point center = getFocusPoint(container);
            int radius = radius();
            Color color = color();
            graphics.setColor(color);
            graphics.fillOval(center.x - radius, center.y - radius, 2 * radius, 2 * radius);
            graphics.setColor(color.darker());
            graphics.drawOval(center.x - radius, center.y - radius, 2 * radius, 2 * radius);

            if (showVelocity || editable && isSelected()) {
                drawVelocity(graphics, container, center);
            }
        } finally {
            graphics.dispose();
        }
    }

    private void drawVelocity(Graphics2D graphics, IContainer container, Point center) {
        Point endpoint = velocityPoint(container);
        graphics.drawLine(center.x, center.y, endpoint.x, endpoint.y);
        double angle = Math.atan2(endpoint.y - center.y, endpoint.x - center.x);
        for (double delta : new double[] {-0.45, 0.45}) {
            graphics.drawLine(endpoint.x, endpoint.y,
                    endpoint.x - (int) (8 * Math.cos(angle + delta)),
                    endpoint.y - (int) (8 * Math.sin(angle + delta)));
        }
        if (editable && isSelected() && hasVelocityHandle(container)) {
            graphics.fillRect(endpoint.x - 4, endpoint.y - 4, 8, 8);
        }
    }

    @Override
    public boolean contains(IContainer container, Point point) {
        Point center = getFocusPoint(container);
        if (center.distance(point) <= radius() + 4) {
            return true;
        }
        return editable && isSelected() && hasVelocityHandle(container)
                && velocityPoint(container).distance(point) <= 8;
    }

    @Override
    public void startModification() {
        editingVelocity = false;
        if (_modification != null && editable && isSelected()
                && hasVelocityHandle(_modification.getContainer())
                && velocityPoint(_modification.getContainer())
                        .distance(_modification.getStartMousePoint()) <= 10) {
            editingVelocity = true;
            _modification.setType(ItemModification.ModificationType.DRAG);
            return;
        }
        super.startModification();
    }

    @Override
    public void modify() {
        if (!editingVelocity) {
            super.modify();
            return;
        }

        Point2D.Double endpoint = new Point2D.Double();
        _modification.getContainer().localToWorld(
                _modification.getCurrentMousePoint(), endpoint);
        Point2D.Double center = getFocus();
        body = new Body(body.id(), body.mass(), center.x, center.y,
                (endpoint.x - center.x) / VELOCITY_ARROW_SCALE,
                (endpoint.y - center.y) / VELOCITY_ARROW_SCALE);
        setDirty(true);
        _modification.getContainer().refresh();
    }

    @Override
    public void stopModification() {
        super.stopModification();
        editingVelocity = false;
    }

    @Override
    public void doubleClicked(MouseEvent event) {
        if (editable) {
            edit(getContainer().getComponent());
        }
    }

    @Override
    public void getFeedbackStrings(IContainer container, Point screenPoint,
            Point2D.Double worldPoint, List<String> feedback) {
        Body current = body();
        feedback.add(String.format(Locale.ROOT,
                "$cyan$Body %d  m=%.6g", current.id(), current.mass()));
        feedback.add(String.format(Locale.ROOT,
                "x=%.6g  y=%.6g  vx=%.6g  vy=%.6g",
                current.x(), current.y(), current.vx(), current.vy()));
    }

    void edit(Component parent) {
        Body current = body();
        JTextField mass = numberField(current.mass());
        JTextField x = numberField(current.x());
        JTextField y = numberField(current.y());
        JTextField vx = numberField(current.vx());
        JTextField vy = numberField(current.vy());

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 4));
        addField(panel, "Mass", mass);
        addField(panel, "x", x);
        addField(panel, "y", y);
        addField(panel, "vx", vx);
        addField(panel, "vy", vy);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Edit body " + current.id(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            update(new Body(current.id(),
                    Double.parseDouble(mass.getText()),
                    Double.parseDouble(x.getText()),
                    Double.parseDouble(y.getText()),
                    Double.parseDouble(vx.getText()),
                    Double.parseDouble(vy.getText())));
            editor.edit(this);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Enter finite numbers and a positive mass.",
                    "Invalid body", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void addField(JPanel panel, String label, JComponent field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private static JTextField numberField(double value) {
        JTextField field = new JTextField(SmartDoubleFormatter.doubleFormat(value, 8), 10);
        field.setCaretPosition(0);
        return field;
    }
}
