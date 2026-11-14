package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramClipboardTest {

    @Test
    public void sensitiveFlagNeedsAndroid13() {
        assertFalse(NanogramClipboard.supportsSensitiveFlag(31));
        assertFalse(NanogramClipboard.supportsSensitiveFlag(32));
        assertTrue(NanogramClipboard.supportsSensitiveFlag(33));
        assertTrue(NanogramClipboard.supportsSensitiveFlag(36));
    }

    @Test
    public void nullClipIsIgnored() {
        NanogramClipboard.markSensitive(null);
    }

    @Test
    public void onlyPrivateInviteLinksAreSensitive() {
        assertTrue(NanogramClipboard.isPrivateInviteLink("https://t.me/+AbCdEf"));
        assertTrue(NanogramClipboard.isPrivateInviteLink("https://t.me/joinchat/AbCdEf"));
        assertFalse(NanogramClipboard.isPrivateInviteLink("https://t.me/durov"));
        assertFalse(NanogramClipboard.isPrivateInviteLink(null));
    }
}
