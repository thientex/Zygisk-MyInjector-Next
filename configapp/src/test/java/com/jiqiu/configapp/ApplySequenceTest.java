package com.jiqiu.configapp;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ApplySequenceTest {
    @Test public void changedLibraryIsPublishedBeforeTargetIsClosedExactlyOnce() {
        List<String> events = new ArrayList<>();
        ApplySequence.Outcome result = ApplySequence.run(true,
            () -> { events.add("deploy"); return true; },
            () -> { events.add("publish"); return true; },
            () -> { events.add("close"); return true; });
        assertEquals(ApplySequence.Outcome.RESTARTED, result);
        assertEquals(Arrays.asList("deploy", "publish", "close"), events);
    }
    @Test public void failedDeploymentDoesNotPublishOrCloseApp() {
        assertEquals(ApplySequence.Outcome.FAILED, ApplySequence.run(true,
            () -> false, () -> { fail("must not publish"); return true; },
            () -> { fail("must not close target"); return true; }));
    }
    @Test public void failedPublicationDoesNotCloseApp() {
        assertEquals(ApplySequence.Outcome.FAILED, ApplySequence.run(true,
            () -> true, () -> false, () -> { fail("must not close target"); return true; }));
    }
    @Test public void disabledTargetIsSavedWithoutBeingClosed() {
        assertEquals(ApplySequence.Outcome.SAVED, ApplySequence.run(false,
            () -> { fail("must not deploy"); return true; }, () -> true,
            () -> { fail("must not close disabled target"); return true; }));
    }
    @Test public void stopFailureIsDistinctFromSaveFailure() {
        assertEquals(ApplySequence.Outcome.STOP_FAILED,
            ApplySequence.run(true, () -> true, () -> true, () -> false));
    }
}
