package br.com.falai;

/** Request pacing, independent of Android so server backoff can be tested locally. */
final class UpdateCheckPolicy {
    static final long INTERVAL_MS = 5 * 60_000L;
    static boolean limited(int status, String remaining) {
        return status == 403 || status == 429 || "0".equals(remaining);
    }
    static long nextAllowed(long now, int status, String remaining, String reset, String retryAfter, long serverDate) {
        long next = now + INTERVAL_MS;
        if (!limited(status, remaining)) return next;
        long serverNow = serverDate > 0 ? serverDate : now;
        long resetAt = millis(reset), retry = millis(retryAfter);
        if ("0".equals(remaining)) {
            long delay = resetAt > serverNow ? resetAt - serverNow + 1000 : 60 * 60_000L;
            next = Math.max(next, now + delay);
        }
        if (retry > 0) next = Math.max(next, now + retry + 1000);
        return next;
    }
    private static long millis(String seconds) {
        try { return Math.max(0, Math.multiplyExact(Long.parseLong(seconds), 1000)); }
        catch (NumberFormatException | ArithmeticException ignored) { return 0; }
    }
    private UpdateCheckPolicy() { }
}
