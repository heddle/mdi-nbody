package edu.cnu.mdi.nbody.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.cnu.mdi.container.BaseContainer;
import edu.cnu.mdi.container.BaseToolHandler;
import edu.cnu.mdi.graphics.toolbar.BaseToolBar;
import edu.cnu.mdi.graphics.toolbar.GestureContext;
import edu.cnu.mdi.nbody.model.Body;
import edu.cnu.mdi.nbody.model.NBodySetup;

/**
 * Undo and redo on the canvas must leave the drawn bodies and the setup that
 * Start will run agreeing with each other, for every way a body can be edited.
 */
class NBodyUndoTest {

    private static final double EPS = 1.0e-9;

    private NBodyView view;
    private BaseContainer container;
    private BodyItem item;

    @BeforeEach
    void openPausedView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            view = new NBodyView(new EnergyPlotView(), new AngularMomentumPlotView(), new BodyInfoView());
            container = (BaseContainer) view.getIContainer();
            container.setBounds(0, 0, 800, 640);
            container.setDirty(true);
        });
        // The engine starts and immediately pauses; bodies unlock when it has.
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 500 && !ready.get(); i++) {
            Thread.sleep(10);
            SwingUtilities.invokeAndWait(() -> container.getDefaultLayer().getAllItems().stream()
                    .filter(BodyItem.class::isInstance).map(BodyItem.class::cast).findFirst()
                    .ifPresent(first -> {
                        item = first;
                        ready.set(first.isDraggable());
                    }));
        }
        if (!ready.get()) {
            fail("the view never became editable");
        }
    }

    @AfterEach
    void closeView() throws Exception {
        SwingUtilities.invokeAndWait(view::prepareForExit);
    }

    @Test
    void undoingAMoveRestoresTheCanvasAndTheSetup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Body start = item.body();
            drag(item.getFocusPoint(container), 120, 60);
            Body moved = item.body();

            container.undo();
            assertBody(start, item.body());
            assertBody(start, inSetup(start.id()));

            container.redo();
            assertBody(moved, item.body());
            assertBody(moved, inSetup(start.id()));
        });
    }

    @Test
    void aVelocityDragIsItsOwnUndoStep() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Body start = item.body();
            drag(item.getFocusPoint(container), 120, 60);
            Body moved = item.body();

            item.setSelected(true);
            drag(velocityHandle(), 40, -40);
            Body faster = item.body();
            assertFalse(faster.equals(moved), "the velocity changed");

            container.undo();
            assertBody(moved, item.body());
            assertBody(moved, inSetup(start.id()));

            container.redo();
            assertBody(faster, item.body());
            assertBody(faster, inSetup(start.id()));
        });
    }

    @Test
    void aDialogEditIsItsOwnUndoStep() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Body start = item.body();
            drag(item.getFocusPoint(container), 120, 60);
            Body moved = item.body();

            Body typed = new Body(moved.id(), 3.5, 1.25, -0.75, 0.2, 0.4);
            item.applyEdit(typed);
            assertBody(typed, inSetup(start.id()));

            container.undo();
            assertBody(moved, item.body());
            assertBody(moved, inSetup(start.id()));

            container.undo();
            assertBody(start, item.body());
            assertBody(start, inSetup(start.id()));

            container.redo();
            container.redo();
            assertBody(typed, item.body());
            assertBody(typed, inSetup(start.id()));
        });
    }

    // ----------------------------------------------------------------- helpers

    private Point velocityHandle() {
        Body body = item.body();
        Point point = new Point();
        // The arrow tip is drawn at position + 0.4 * velocity.
        container.worldToLocal(point, body.x() + 0.4 * body.vx(), body.y() + 0.4 * body.vy());
        return point;
    }

    private void drag(Point from, int dx, int dy) {
        BaseToolHandler handler = new BaseToolHandler(container);
        BaseToolBar bar = new BaseToolBar(container.getComponent(), handler, 0L);
        Point to = new Point(from.x + dx, from.y + dy);
        MouseEvent press = mouse(MouseEvent.MOUSE_PRESSED, from);
        MouseEvent move = mouse(MouseEvent.MOUSE_DRAGGED, to);
        handler.beginDragObject(new GestureContext(bar, container.getComponent(), item, from, press));
        handler.dragObjectBy(new GestureContext(bar, container.getComponent(), item, to, move), dx, dy);
        handler.endDragObject(new GestureContext(bar, container.getComponent(), item, to, move));
    }

    private MouseEvent mouse(int id, Point p) {
        return new MouseEvent(container.getComponent(), id, 0, MouseEvent.BUTTON1_DOWN_MASK,
                p.x, p.y, 1, false, MouseEvent.BUTTON1);
    }

    private Body inSetup(int id) {
        try {
            var field = NBodyView.class.getDeclaredField("setup");
            field.setAccessible(true);
            return ((NBodySetup) field.get(view)).bodies().stream()
                    .filter(body -> body.id() == id).findFirst().orElseThrow();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertBody(Body expected, Body actual) {
        assertEquals(expected.mass(), actual.mass(), EPS, "mass");
        assertEquals(expected.x(), actual.x(), EPS, "x");
        assertEquals(expected.y(), actual.y(), EPS, "y");
        assertEquals(expected.vx(), actual.vx(), EPS, "vx");
        assertEquals(expected.vy(), actual.vy(), EPS, "vy");
    }
}
