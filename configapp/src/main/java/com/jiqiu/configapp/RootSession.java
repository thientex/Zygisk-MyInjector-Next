package com.jiqiu.configapp;

/** Root authorization must not be inferred from a cached non-root shell. */
final class RootSession {
    interface Session {
        boolean alive();
        boolean root();
        boolean uidIsRoot();
        void close();
    }
    interface Backend {
        Session cached();
        Session open();
    }
    static boolean check(Backend backend) {
        Session session = backend.cached();
        try {
            if (valid(session)) return true;
            if (session != null) session.close();
            session = backend.open();
            if (valid(session)) return true;
        } catch (RuntimeException ignored) {
            // An unavailable su binary or a shell dying mid-probe is a denial.
        }
        if (session != null) session.close();
        return false;
    }
    private static boolean valid(Session session) {
        return session != null && session.alive() && session.root() && session.uidIsRoot();
    }
}
