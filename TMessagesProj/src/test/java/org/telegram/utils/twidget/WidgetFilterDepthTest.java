package org.telegram.utils.twidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetFilterDepthTest {

    @Test
    public void sixteenNestedFiltersAreDrawn() {
        assertEquals(16, WidgetSandboxPainter.MAX_NESTED_FILTERS);
        for (int open = 0; open < 16; open++) {
            assertTrue("level " + (open + 1), WidgetSandboxPainter.canNestFilter(open));
        }
    }

    @Test
    public void deeperFiltersDrawUnfiltered() {
        assertFalse(WidgetSandboxPainter.canNestFilter(16));
        assertFalse(WidgetSandboxPainter.canNestFilter(2000));
    }
}
