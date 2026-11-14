package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NanogramReportPartsTest {

    @Test
    public void everyFolderPresentMeansNoneMissing() {
        assertEquals("  none\n", NanogramDiagnostics.missingParts(Arrays.asList(
                "crashes/a.txt", "traces/b.txt", "profiles/c.perfetto-trace", "logs/d.txt", "voip/e.txt", "README.txt")));
    }

    @Test
    public void emptyFoldersAreListedWithReason() {
        final String text = NanogramDiagnostics.missingParts(Arrays.asList("logs/d.txt", "device-info.txt"));
        assertTrue(text.contains("crashes/ missing"));
        assertTrue(text.contains("traces/ missing"));
        assertTrue(text.contains("profiles/ missing"));
        assertTrue(text.contains("voip/ missing"));
        assertFalse(text.contains("logs/ missing"));
    }

    @Test
    public void failedFilePartsAreListedWithTheExceptionClass() {
        final String text = NanogramDiagnostics.missingParts(Arrays.asList(
                "crashes/a.txt", "traces/b.txt", "profiles/c.perfetto-trace", "logs/d.txt", "voip/e.txt"),
                Arrays.asList("health-energy.txt: java.lang.SecurityException"));
        assertTrue(text.contains("health-energy.txt failed: java.lang.SecurityException"));
        assertFalse(text.contains("none"));
    }
}
