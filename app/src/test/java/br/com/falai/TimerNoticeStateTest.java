package br.com.falai;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimerNoticeStateTest {
    @Test public void enabledChatStillShowsNoticeWithMissingOrUnavailableHistory() {
        for (long hours : new long[]{24, 168, 2160}) {
            assertTrue(TimerNoticeState.needsNotice(hours, "alice", 1000, Collections.emptyList()));
            assertTrue(TimerNoticeState.needsNotice(hours, "bob", 1000, Collections.emptyList()));
        }
    }
    @Test public void legacyEnabledChatWithoutAuthorOrDateStillHasAnExplanation() {
        assertTrue(TimerNoticeState.needsNotice(24L, null, 0, Collections.emptyList()));
    }
    @Test public void existingEventPreventsDuplicateFallback() {
        assertFalse(TimerNoticeState.needsNotice(24L, "alice", 1000,
                Arrays.asList(new TimerNoticeState.Change(24, "alice", 1000))));
    }
    @Test public void staleEventCannotHideNewSettingEvenWithSameDuration() {
        assertTrue(TimerNoticeState.needsNotice(24L, "alice", 2000,
                Arrays.asList(new TimerNoticeState.Change(24, "alice", 1000))));
        assertTrue(TimerNoticeState.needsNotice(24L, "bob", 1000,
                Arrays.asList(new TimerNoticeState.Change(24, "alice", 1000))));
    }
    @Test public void explicitDisableIsShownButUnconfiguredChatIsNotInvented() {
        assertTrue(TimerNoticeState.needsNotice(0L, "alice", 2000, Collections.emptyList()));
        assertFalse(TimerNoticeState.needsNotice(0L, null, 0, Collections.emptyList()));
        assertFalse(TimerNoticeState.needsNotice(null, null, 0, Collections.emptyList()));
        assertFalse(TimerNoticeState.needsNotice(48L, "alice", 1000, Collections.emptyList()));
    }
}
