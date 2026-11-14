package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NanogramMediaCapabilitiesTest {

    private static NanogramMediaCapabilities.Candidate c(String name, boolean hw, boolean sw, boolean ok) {
        return new NanogramMediaCapabilities.Candidate(name, hw, sw, ok);
    }

    @Test
    public void hardwareNeedsAcceleratedAndNotSoftwareOnly() {
        assertTrue(NanogramMediaCapabilities.isHardware(true, false));
        assertFalse(NanogramMediaCapabilities.isHardware(true, true));
        assertFalse(NanogramMediaCapabilities.isHardware(false, false));
    }

    @Test
    public void picksFirstHardwareThatSupportsTheRequest() {
        assertEquals("c2.qti.avc.encoder", NanogramMediaCapabilities.pickHardware(Arrays.asList(
                c("c2.android.avc.encoder", false, true, true),
                c("c2.small.avc.encoder", true, false, false),
                c("c2.qti.avc.encoder", true, false, true),
                c("c2.other.avc.encoder", true, false, true))));
    }

    @Test
    public void noHardwareMeansNull() {
        assertNull(NanogramMediaCapabilities.pickHardware(Arrays.asList(c("c2.android.avc.encoder", false, true, true))));
        assertNull(NanogramMediaCapabilities.pickHardware(Arrays.<NanogramMediaCapabilities.Candidate>asList()));
    }

    @Test
    public void hardwareFirstIsStableAndKeepsListWhenNothingToMove() {
        List<String> items = Arrays.asList("sw1", "hw1", "sw2", "hw2");
        assertEquals(Arrays.asList("hw1", "hw2", "sw1", "sw2"), NanogramMediaCapabilities.hardwareFirst(items, s -> s.startsWith("hw")));
        assertSame(items, NanogramMediaCapabilities.hardwareFirst(items, s -> false));
        assertSame(items, NanogramMediaCapabilities.hardwareFirst(items, s -> true));
    }

    @Test
    public void autoRules() {
        assertTrue(NanogramMediaCapabilities.autoGpuPreupload(31, false));
        assertTrue(NanogramMediaCapabilities.autoGpuPreupload(34, false));
        assertFalse(NanogramMediaCapabilities.autoGpuPreupload(0, false));
        assertFalse(NanogramMediaCapabilities.autoGpuPreupload(33, true));
        assertFalse(NanogramMediaCapabilities.autoAdaptiveRefresh(34));
        assertTrue(NanogramMediaCapabilities.autoAdaptiveRefresh(35));
    }

    @Test
    public void storedChoiceBeatsAutomatic() {
        assertTrue(NanogramExperiments.resolve(null, true));
        assertFalse(NanogramExperiments.resolve(null, false));
        assertFalse(NanogramExperiments.resolve(false, true));
        assertTrue(NanogramExperiments.resolve(true, false));
    }
}
