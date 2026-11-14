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
    public void everyDisconnectCodeIsOneTheSystemAccepts() {
        for (int mask = 0; mask < 16; mask++) {
            int code = NanogramTelecomCall.disconnectCode((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
            assertTrue(code == DisconnectCause.LOCAL || code == DisconnectCause.REMOTE
                    || code == DisconnectCause.MISSED || code == DisconnectCause.REJECTED);
        }
    }

    @Test
    public void outgoingCallsStartWithoutPrivateCallYet() {
        boolean oneToOne = NanogramTelecomCall.oneToOne(false, true);
        assertTrue(NanogramTelecomCall.startsCall(false, true, oneToOne));
        assertTrue(NanogramTelecomCall.startsCall(true, false, oneToOne));
        assertFalse(NanogramTelecomCall.startsCall(false, false, oneToOne));
    }

    @Test
    public void groupCallsAndMissingUserDoNotStart() {
        assertFalse(NanogramTelecomCall.startsCall(true, false, NanogramTelecomCall.oneToOne(true, true)));
        assertFalse(NanogramTelecomCall.startsCall(true, false, NanogramTelecomCall.oneToOne(false, false)));
    }

    @Test
    public void disconnectCodeMapping() {
        assertEquals(DisconnectCause.LOCAL, NanogramTelecomCall.disconnectCode(true, true, true, true));
        assertEquals(DisconnectCause.REMOTE, NanogramTelecomCall.disconnectCode(false, true, false, false));
        assertEquals(DisconnectCause.REJECTED, NanogramTelecomCall.disconnectCode(false, false, false, true));
        assertEquals(DisconnectCause.MISSED, NanogramTelecomCall.disconnectCode(false, false, true, false));
        assertEquals(DisconnectCause.LOCAL, NanogramTelecomCall.disconnectCode(false, false, false, false));
    }
}
