package edu.cnu.mdi.nbody.view;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.*;
import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.item.Layer;
import edu.cnu.mdi.item.ItemModification;
import edu.cnu.mdi.item.PointItem;
import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.util.SmartDoubleFormatter;

/** EDT-owned interactive representation of one body. Numerical state remains in snapshots. */
final class BodyItem extends PointItem {
    interface Editor { void edit(BodyItem item); }
    private Body body;
    private final Editor editor;
    private boolean showVelocity,editable;
    private boolean editingVelocity;

    BodyItem(Layer layer, Body body, Editor editor) {
        super(layer,new Point2D.Double(body.x(),body.y()));
        this.body=body; this.editor=editor;
        setDisplayName("Body " + body.id());
        setDraggable(false); setDeletable(false); setResizable(false); setLocked(false);
        setDoubleClickable(true); setStyleEditable(false);
    }
    Body body() {
        Point2D.Double p=getFocus();
        if (p==null) return body;
        return new Body(body.id(),body.mass(),p.x,p.y,body.vx(),body.vy());
    }
    void update(Body next) { body=next; setFocus(new Point2D.Double(next.x(),next.y())); setDirty(true); }
    void setShowVelocity(boolean show) { showVelocity=show; setDirty(true); }
    void setEditable(boolean editable,boolean deletable) {
        this.editable=editable;
        setLocked(false); setSelectable(true); setDoubleClickable(editable);
        setDraggable(editable); setDeletable(editable && deletable);
    }
    private int radius() { return (int)Math.max(4,Math.min(14,7*Math.cbrt(body.mass()))); }
    private Color color() { return Color.getHSBColor((float)((body.id()*.61803398875)%1),.7f,.8f); }
    @Override public Rectangle getBounds(IContainer container) {
        Point p=getFocusPoint(container); int r=radius()+4;
        Rectangle bounds=new Rectangle(p.x-r,p.y-r,2*r,2*r);
        if ((showVelocity || editable && isSelected()) && hasVelocityHandle(container)) bounds.add(velocityPoint(container));
        return bounds;
    }
    private Point velocityPoint(IContainer container) {
        Body current=body(); Point point=new Point();
        container.worldToLocal(point,current.x()+current.vx()*.4,current.y()+current.vy()*.4); return point;
    }
    private boolean hasVelocityHandle(IContainer container) {
        return getFocusPoint(container).distance(velocityPoint(container))>radius()+4;
    }
    @Override public void drawItem(Graphics2D original,IContainer container) {
        Graphics2D g=(Graphics2D)original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Point a=getFocusPoint(container); int r=radius(); Color c=color();
            g.setColor(c); g.fillOval(a.x-r,a.y-r,2*r,2*r);
            g.setColor(c.darker()); g.drawOval(a.x-r,a.y-r,2*r,2*r);
            if (showVelocity || editable && isSelected()) {
                Body current=body();
                Point b=velocityPoint(container);
                g.drawLine(a.x,a.y,b.x,b.y);
                double angle=Math.atan2(b.y-a.y,b.x-a.x);
                for (double delta:new double[]{-.45,.45}) g.drawLine(b.x,b.y,
                        b.x-(int)(8*Math.cos(angle+delta)),b.y-(int)(8*Math.sin(angle+delta)));
                if (editable && isSelected() && hasVelocityHandle(container)) { g.fillRect(b.x-4,b.y-4,8,8); }
            }
        } finally { g.dispose(); }
    }
    @Override public boolean contains(IContainer container,Point point) {
        Point center=getFocusPoint(container); int r=radius()+4;
        if (center.distance(point)<=r) return true;
        return editable && isSelected() && hasVelocityHandle(container) && velocityPoint(container).distance(point)<=8;
    }
    @Override public void startModification() {
        editingVelocity=false;
        if (_modification!=null && editable && isSelected() && hasVelocityHandle(_modification.getContainer())
                && velocityPoint(_modification.getContainer()).distance(_modification.getStartMousePoint())<=10) {
            editingVelocity=true; _modification.setType(ItemModification.ModificationType.DRAG); return;
        }
        super.startModification();
    }
    @Override public void modify() {
        if (!editingVelocity) { super.modify(); return; }
        Point2D.Double point=new Point2D.Double();
        _modification.getContainer().localToWorld(_modification.getCurrentMousePoint(),point);
        Point2D.Double center=getFocus();
        body=new Body(body.id(),body.mass(),center.x,center.y,(point.x-center.x)/.4,(point.y-center.y)/.4);
        setDirty(true); _modification.getContainer().refresh();
    }
    @Override public void stopModification() { super.stopModification(); editingVelocity=false; }
    @Override public void doubleClicked(MouseEvent event) { if (editable) edit(getContainer().getComponent()); }
    @Override public void getFeedbackStrings(IContainer container,Point pp,Point2D.Double wp,List<String> feedback) {
        Body b=body();
        feedback.add(String.format(java.util.Locale.ROOT,"$cyan$Body %d  m=%.6g",b.id(),b.mass()));
        feedback.add(String.format(java.util.Locale.ROOT,"x=%.6g  y=%.6g  vx=%.6g  vy=%.6g",b.x(),b.y(),b.vx(),b.vy()));
    }
    void edit(Component parent) {
        Body b=body();
        JTextField mass=numberField(b.mass()), x=numberField(b.x()), y=numberField(b.y()),
                vx=numberField(b.vx()), vy=numberField(b.vy());
        JPanel panel=new JPanel(new GridLayout(0,2,6,4));
        for (Object[] row:new Object[][]{{"Mass",mass},{"x",x},{"y",y},{"vx",vx},{"vy",vy}}) {
            panel.add(new JLabel((String)row[0])); panel.add((JComponent)row[1]);
        }
        if (JOptionPane.showConfirmDialog(parent,panel,"Edit body " + b.id(),JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;
        try {
            update(new Body(b.id(),Double.parseDouble(mass.getText()),Double.parseDouble(x.getText()),
                    Double.parseDouble(y.getText()),Double.parseDouble(vx.getText()),Double.parseDouble(vy.getText())));
            editor.edit(this);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent,"Enter finite numbers and a positive mass.","Invalid body",JOptionPane.ERROR_MESSAGE);
        }
    }
    private static JTextField numberField(double value) {
        JTextField field=new JTextField(SmartDoubleFormatter.doubleFormat(value,8),10);
        field.setCaretPosition(0);
        return field;
    }
}
