package org.telegram.messenger.nanogram;

import android.text.TextUtils;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;

import java.io.File;

public class NanogramLocalMedia {

    private NanogramLocalMedia() {
    }

    /** An existing file for the message media, or null when nothing is on disk yet. */
    public static File cachedFile(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return null;
        }
        FileLoader fileLoader = FileLoader.getInstance(message.currentAccount);
        if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
            File f = new File(message.messageOwner.attachPath);
            if (f.exists()) {
                return f;
            }
        }
        File f = fileLoader.getPathToMessage(message.messageOwner, true, true);
        if (f.exists()) {
            return f;
        }
        f = fileLoader.getPathToMessage(message.messageOwner);
        if (f.exists()) {
            return f;
        }
        if (message.qualityToSave != null) {
            f = fileLoader.getPathToAttach(message.qualityToSave, null, false, true);
            if (f.exists()) {
                return f;
            }
        }
        message.updateQualitiesCached(true);
        if (message.cachedQuality != null && message.cachedQuality.isCached()) {
            f = new File(message.cachedQuality.uri.getPath());
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }
}
