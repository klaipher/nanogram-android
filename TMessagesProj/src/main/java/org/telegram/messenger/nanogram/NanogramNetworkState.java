package org.telegram.messenger.nanogram;

/**
 * Cache of the default network's properties, fed by a ConnectivityManager default NetworkCallback.
 * Pure logic: the network handle is an opaque object so it can be tested on the JVM.
 */
public final class NanogramNetworkState {

    private Object network;
    private boolean hasInternet;
    private boolean metered;
    private boolean roaming;
    private int transport;
    private volatile boolean initialized;
    private volatile boolean online;

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isOnline() {
        return online;
    }

    public synchronized int transport() {
        return transport;
    }

    public synchronized boolean isMetered() {
        return metered;
    }

    public synchronized boolean isRoaming() {
        return roaming;
    }

    /** Returns true if something callers care about changed. */
    public synchronized boolean onCapabilities(Object newNetwork, boolean internet, boolean newMetered, boolean newRoaming, int newTransport) {
        final boolean changed = !initialized || network != newNetwork || hasInternet != internet || metered != newMetered
                || roaming != newRoaming || transport != newTransport;
        network = newNetwork;
        hasInternet = internet;
        metered = newMetered;
        roaming = newRoaming;
        transport = newTransport;
        online = internet;
        initialized = true;
        return changed;
    }

    /** Returns true if the lost network was the current one. A stale onLost for a replaced network is ignored. */
    public synchronized boolean onLost(Object lostNetwork) {
        if (initialized && network != null && network != lostNetwork) {
            return false;
        }
        network = null;
        hasInternet = false;
        metered = false;
        roaming = false;
        transport = 0;
        online = false;
        initialized = true;
        return true;
    }
}
