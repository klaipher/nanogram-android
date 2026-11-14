package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramReportMaskingTest {

    @Test
    public void phoneNumbersAndEmailsAreMasked() {
        assertEquals("call [phone] or mail [email] now", NanogramReportMasking.mask("call +380 (67) 123-45-67 or mail Someone.Name@example.org now"));
    }

    @Test
    public void numbersWithoutPlusAndShortOnesStay() {
        assertEquals("id 1234567890 dc +12 v13.2", NanogramReportMasking.mask("id 1234567890 dc +12 v13.2"));
    }

    @Test
    public void onlyTextLogsAreMasked() {
        assertTrue(NanogramReportMasking.isMaskedTextFile("2026_11_14_net.txt"));
        assertTrue(NanogramReportMasking.isMaskedTextFile("app.log"));
        assertFalse(NanogramReportMasking.isMaskedTextFile("trace.perfetto-trace"));
        assertFalse(NanogramReportMasking.isMaskedTextFile("exit.json"));
    }
}
