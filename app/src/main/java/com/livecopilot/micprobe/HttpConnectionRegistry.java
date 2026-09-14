package com.livecopilot.micprobe;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HttpConnectionRegistry {
    private final Set<HttpURLConnection> active = new HashSet<>();

    synchronized void register(HttpURLConnection connection) {
        if (connection != null) active.add(connection);
    }

    synchronized void unregister(HttpURLConnection connection) {
        if (connection != null) active.remove(connection);
    }

    int cancelAll() {
        List<HttpURLConnection> snapshot;
        synchronized (this) {
            if (active.isEmpty()) return 0;
            snapshot = new ArrayList<>(active);
            active.clear();
        }
        for (HttpURLConnection connection : snapshot) {
            try { connection.disconnect(); } catch (Throwable ignored) {}
        }
        return snapshot.size();
    }

    synchronized int size() {
        return active.size();
    }
}
