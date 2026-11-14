package org.telegram.messenger.voip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.telecom.DisconnectCause;

import org.junit.Test;

public class NanogramTelecomCallTest {

    @Test
    public void needsAndroid14ExperimentAndNoLegacyPath() {
        assertTrue(NanogramTelecomCall.shouldUse(34, true, false, true));
        assertFalse(NanogramTelecomCall.shouldUse(33, true, false, true));
        assertFalse(NanogramTelecomCall.shouldUse(34, false, false, true));
        assertFalse(NanogramTelecomCall.shouldUse(34, true, true, true));
    }

    @Test
    public void groupCallsStayOnTheCurrentPath() {
        assertFalse(NanogramTelecomCall.shouldUse(36, true, false, false));
    }

    @Test
    public void disconnectCodeMapping() {
        assertEquals(DisconnectCause.ERROR, NanogramTelecomCall.disconnectCode(true, true, true, true));
        assertEquals(DisconnectCause.BUSY, NanogramTelecomCall.disconnectCode(false, true, false, false));
        assertEquals(DisconnectCause.REJECTED, NanogramTelecomCall.disconnectCode(false, false, false, true));
        assertEquals(DisconnectCause.MISSED, NanogramTelecomCall.disconnectCode(false, false, true, false));
        assertEquals(DisconnectCause.LOCAL, NanogramTelecomCall.disconnectCode(false, false, false, false));
    }
}
