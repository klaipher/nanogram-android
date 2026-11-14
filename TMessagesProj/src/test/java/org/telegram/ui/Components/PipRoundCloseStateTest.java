package org.telegram.ui.Components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PipRoundCloseStateTest {

    @Test
    public void closedWindowIgnoresEveryClose() {
        assertEquals(PipRoundCloseState.NONE, PipRoundCloseState.closeAction(true, true, false, true, true));
        assertEquals(PipRoundCloseState.NONE, PipRoundCloseState.closeAction(false, true, false, true, true));
    }

    @Test
    public void secondAnimatedCloseIsIgnoredButImmediateCloseStillRemoves() {
        assertEquals(PipRoundCloseState.ANIMATE, PipRoundCloseState.closeAction(true, false, false, true, true));
        assertEquals(PipRoundCloseState.NONE, PipRoundCloseState.closeAction(true, false, true, true, true));
        assertEquals(PipRoundCloseState.REMOVE_NOW, PipRoundCloseState.closeAction(false, false, true, true, true));
    }

    @Test
    public void animatedCloseWithoutTextureParentRemovesAtOnce() {
        assertEquals(PipRoundCloseState.REMOVE_NOW, PipRoundCloseState.closeAction(true, false, false, true, false));
        assertEquals(PipRoundCloseState.REMOVE_NOW, PipRoundCloseState.closeAction(true, false, false, false, true));
    }

    @Test
    public void showAnimationsAreBlockedWhileClosingOrClosed() {
        assertFalse(PipRoundCloseState.canShowTemporary(false, true, true));
        assertFalse(PipRoundCloseState.canShowTemporary(true, false, true));
        assertFalse(PipRoundCloseState.canShowTemporary(false, false, false));
        assertTrue(PipRoundCloseState.canShowTemporary(false, false, true));
        assertFalse(PipRoundCloseState.canAnimate(true, false, true, true));
        assertTrue(PipRoundCloseState.canAnimate(false, false, true, true));
        assertFalse(PipRoundCloseState.canAnimate(false, true, false, true));
    }
}
