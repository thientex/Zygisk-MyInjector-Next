package com.jiqiu.configapp;
import org.junit.Test;
import static org.junit.Assert.*;

public class RootSessionTest {
    static class FakeSession implements RootSession.Session {
        boolean root, alive = true, closed;
        FakeSession(boolean root) { this.root = root; }
        public boolean alive() { return alive && !closed; }
        public boolean root() { return root; }
        public boolean uidIsRoot() { return root; }
        public void close() { closed = true; }
    }
    static class FakeBackend implements RootSession.Backend {
        FakeSession cached;
        boolean granted;
        int opens;
        public RootSession.Session cached() { return cached != null && !cached.closed ? cached : null; }
        public RootSession.Session open() { opens++; return cached = new FakeSession(granted); }
    }
    @Test public void denialThenGrantWorksInSameProcess() {
        FakeBackend b = new FakeBackend();
        assertFalse(RootSession.check(b));
        assertTrue(b.cached.closed);
        b.granted = true;
        assertTrue(RootSession.check(b));
        assertEquals(2, b.opens);
    }
    @Test public void oldNonRootCacheIsDiscardedBeforeCheckingAgain() {
        FakeBackend b = new FakeBackend();
        FakeSession denied = b.cached = new FakeSession(false);
        b.granted = true;
        assertTrue(RootSession.check(b));
        assertTrue(denied.closed);
    }
    @Test public void deadRootSessionIsReopened() {
        FakeBackend b = new FakeBackend();
        b.cached = new FakeSession(true); b.cached.alive = false; b.granted = true;
        assertTrue(RootSession.check(b));
        assertEquals(1, b.opens);
    }
    @Test public void rootFlagAloneIsInsufficient() {
        FakeBackend b = new FakeBackend();
        b.cached = new FakeSession(true) { public boolean uidIsRoot() { return false; } };
        assertFalse(RootSession.check(b));
    }
}
