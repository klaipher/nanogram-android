package org.telegram.messenger.nanogram;

public final class NanogramFolderPins {

    private NanogramFolderPins() {
    }

    // pinned chats already sit in alwaysShow, so the existing pin count cancels out of the check
    public static int maxPinned(int chatsLimit, int alwaysShowSize, int pinnedCount) {
        return chatsLimit - alwaysShowSize + pinnedCount;
    }
}
