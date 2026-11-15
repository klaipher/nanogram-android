package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The switch and the decoder options of "Enhance voice messages". */
public class NanogramOpusEnhancementTest {

    @Test
    public void theSwitchIsOffByDefault() {
        // On the JVM there is no application context, so this is the fallback every read of the switch ends in.
        assertFalse(NanogramFeatures.enhanceVoiceMessages());
    }

    @Test
    public void switchedOffNothingUsesOurDecoder() {
        assertFalse(NanogramOpusEnhancement.shouldUse(false, true));
        assertFalse(NanogramOpusEnhancement.shouldUse(false, false));
        assertNull(NanogramOpusEnhancement.options(false, true));
    }

    @Test
    public void switchedOnOnlyVoiceMessagesUseIt() {
        assertTrue(NanogramOpusEnhancement.shouldUse(true, true));
        assertFalse(NanogramOpusEnhancement.shouldUse(true, false));
        assertNull(NanogramOpusEnhancement.options(true, false));
    }

    @Test
    public void theOptionsAreNoLaceAndBandwidthExtension() {
        NanogramOpusEnhancement.Options options = NanogramOpusEnhancement.options(true, true);
        assertNotNull(options);
        // 7 = NoLACE; 6 would be LACE. Also what iOS and macOS use.
        assertEquals(7, options.complexity);
        assertEquals(7, NanogramOpusEnhancement.PLAYBACK_COMPLEXITY);
        assertTrue(options.bandwidthExtension);
    }

    @Test
    public void theNativeDecoderIsNotAvailableOnTheJvm() {
        assertFalse(NanogramOpusDecoder.isAvailable());
        assertTrue(NanogramOpusDecoder.supportsChannelCount(1));
        assertTrue(NanogramOpusDecoder.supportsChannelCount(2));
        assertFalse(NanogramOpusDecoder.supportsChannelCount(6));
    }
}
