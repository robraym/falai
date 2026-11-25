package br.com.falai;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateCheckPolicyTest {
    private static final long NOW = 1_800_000_000_000L;
    @Test public void everyAttemptHasFiveMinuteIntervalEvenWhenItFails() {
        for (int status : new int[]{200, 404, 500, 403, 429})
            assertEquals(NOW + 300_000, UpdateCheckPolicy.nextAllowed(NOW, status, "25", null, null, NOW));
    }
    @Test public void depletedQuotaWaitsForResetUsingServerClock() {
        long server = NOW - 600_000;
        String reset = Long.toString((server + 2_400_000) / 1000);
        assertEquals(NOW + 2_401_000, UpdateCheckPolicy.nextAllowed(NOW, 403, "0", reset, null, server));
    }
    @Test public void retryAfterAndResetBothHaveToExpire() {
        String reset = Long.toString((NOW + 600_000) / 1000);
        assertEquals(NOW + 901_000, UpdateCheckPolicy.nextAllowed(NOW, 429, "0", reset, "900", NOW));
        assertEquals(NOW + 601_000, UpdateCheckPolicy.nextAllowed(NOW, 429, "0", reset, "60", NOW));
    }
    @Test public void invalidOrStaleResetFallsBackToOneHourWhenQuotaIsDepleted() {
        for (String reset : new String[]{null, "bad", "-1", "999999999999999999", Long.toString((NOW - 1000) / 1000)})
            assertEquals(NOW + 3_600_000, UpdateCheckPolicy.nextAllowed(NOW, 403, "0", reset, null, NOW));
    }
    @Test public void finalSuccessfulRequestAlsoProtectsDepletedQuota() {
        assertTrue(UpdateCheckPolicy.limited(200, "0"));
        assertFalse(UpdateCheckPolicy.limited(500, "10"));
        assertEquals(NOW + 3_600_000, UpdateCheckPolicy.nextAllowed(NOW, 200, "0", null, null, 0));
    }
}
