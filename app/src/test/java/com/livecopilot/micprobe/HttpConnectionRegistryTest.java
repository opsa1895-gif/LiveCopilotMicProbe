package com.livecopilot.micprobe;

import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpConnectionRegistryTest {
    @Test
    public void cancelAllDisconnectsEveryRegisteredConnectionAndClearsRegistry() throws Exception {
        HttpConnectionRegistry registry = new HttpConnectionRegistry();
        FakeConnection first = new FakeConnection();
        FakeConnection second = new FakeConnection();
        registry.register(first);
        registry.register(second);

        assertEquals(2, registry.size());
        assertEquals(2, registry.cancelAll());
        assertTrue(first.disconnected);
        assertTrue(second.disconnected);
        assertEquals(0, registry.size());
        assertEquals(0, registry.cancelAll());
    }

    @Test
    public void unregisteredConnectionIsNotCancelled() throws Exception {
        HttpConnectionRegistry registry = new HttpConnectionRegistry();
        FakeConnection connection = new FakeConnection();
        registry.register(connection);
        registry.unregister(connection);

        assertEquals(0, registry.cancelAll());
        assertFalse(connection.disconnected);
    }

    private static final class FakeConnection extends HttpURLConnection {
        boolean disconnected;

        FakeConnection() throws Exception {
            super(new URL("http://localhost"));
        }

        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() throws IOException {}
    }
}
