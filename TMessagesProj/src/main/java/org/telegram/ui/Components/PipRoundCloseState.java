package org.telegram.ui.Components;

public final class PipRoundCloseState {

    public static final int NONE = 0;
    public static final int ANIMATE = 1;
    public static final int REMOVE_NOW = 2;

    private PipRoundCloseState() {
    }

    public static int closeAction(boolean animated, boolean closed, boolean closing, boolean hasWindow, boolean hasTexture) {
        if (closed) {
            return NONE;
        }
        if (!animated) {
            return REMOVE_NOW;
        }
        if (closing) {
            return NONE;
        }
        return hasWindow && hasTexture ? ANIMATE : REMOVE_NOW;
    }

    public static boolean canAnimate(boolean show, boolean closed, boolean closing, boolean hasWindow) {
        return hasWindow && !closed && !(show && closing);
    }

    public static boolean canShowTemporary(boolean closed, boolean closing, boolean hasWindow) {
        return hasWindow && !closed && !closing;
    }
}
