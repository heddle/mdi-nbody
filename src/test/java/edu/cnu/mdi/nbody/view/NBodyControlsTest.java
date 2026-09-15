package edu.cnu.mdi.nbody.view;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import edu.cnu.mdi.nbody.model.Presets;

class NBodyControlsTest {
    @Test void recommendedPrecessionValuesSurviveSpinnerCommit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            NBodyControls controls=new NBodyControls();
            JComboBox<?> presets=components(controls,JComboBox.class).stream()
                    .filter(combo -> combo.getItemCount()>0 && combo.getItemAt(0) instanceof Presets.Preset)
                    .findFirst().orElseThrow();
            presets.setSelectedItem(Presets.Preset.MERCURY_JUPITER);
            for (JSpinner spinner:components(controls,JSpinner.class)) assertDoesNotThrow(spinner::commitEdit);
            var texts=components(controls,JSpinner.class).stream()
                    .map(spinner -> ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().getText()).toList();
            assertTrue(texts.stream().anyMatch("0.000100"::equals));
            assertTrue(texts.stream().anyMatch("0.000200"::equals));
        });
    }
    private static <T extends Component> java.util.List<T> components(Container root,Class<T> type) {
        var result=new ArrayList<T>();
        for (Component component:root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(components(child,type));
        }
        return result;
    }
}
