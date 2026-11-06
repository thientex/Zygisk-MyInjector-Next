package com.jiqiu.configapp;

/** A target is closed only after its files and configuration were published. */
final class ApplySequence {
    interface Step { boolean run(); }
    enum Outcome { SAVED, RESTARTED, FAILED, STOP_FAILED }
    static Outcome run(boolean enabled, Step deploy, Step publish, Step stop) {
        if (enabled && !deploy.run()) return Outcome.FAILED;
        if (!publish.run()) return Outcome.FAILED;
        if (!enabled) return Outcome.SAVED;
        return stop.run() ? Outcome.RESTARTED : Outcome.STOP_FAILED;
    }
}
