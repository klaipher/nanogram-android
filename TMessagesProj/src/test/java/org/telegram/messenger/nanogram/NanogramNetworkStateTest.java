package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramNetworkStateTest {

    private final Object wifi = new Object();
    private final Object cell = new Object();

    @Test
    public void startsUninitialized() {
        final NanogramNetworkState state = new NanogramNetworkState();
        assertFalse(state.isInitialized());
        assertFalse(state.isOnline());
    }

    @Test
    public void firstCapabilitiesAlwaysChange() {
        final NanogramNetworkState state = new NanogramNetworkState();
        assertTrue(state.onCapabilities(wifi, true, false, false, 1));
        assertTrue(state.isInitialized());
        assertTrue(state.isOnline());
        assertEquals(1, state.transport());
    }

    @Test
    public void repeatedIdenticalCapabilitiesDoNotChange() {
        final NanogramNetworkState state = new NanogramNetworkState();
        state.onCapabilities(wifi, true, false, false, 1);
        assertFalse(state.onCapabilities(wifi, true, false, false, 1));
        assertTrue(state.onCapabilities(wifi, true, true, false, 1));
    }

    @Test
    public void networkWithoutInternetIsOffline() {
        final NanogramNetworkState state = new NanogramNetworkState();
        state.onCapabilities(wifi, false, false, false, 1);
        assertFalse(state.isOnline());
    }

    @Test
    public void lostCurrentNetworkGoesOffline() {
        final NanogramNetworkState state = new NanogramNetworkState();
        state.onCapabilities(wifi, true, false, false, 1);
        assertTrue(state.onLost(wifi));
        assertFalse(state.isOnline());
        assertTrue(state.isInitialized());
    }

    @Test
    public void staleLostAfterSwitchIsIgnored() {
        final NanogramNetworkState state = new NanogramNetworkState();
        state.onCapabilities(wifi, true, false, false, 1);
        state.onCapabilities(cell, true, true, false, 0);
        assertFalse(state.onLost(wifi));
        assertTrue(state.isOnline());
        assertTrue(state.isMetered());
    }
}
