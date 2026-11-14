package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NanogramCallNotificationTest {

    @Test
    public void emptyNameGetsPlaceholder() {
        assertEquals("___", NanogramCallNotification.personName(null));
        assertEquals("___", NanogramCallNotification.personName(""));
    }

    @Test
    public void realNameIsKept() {
        assertEquals("Anna", NanogramCallNotification.personName("Anna"));
    }
}
