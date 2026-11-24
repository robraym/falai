package br.com.falai;

import java.util.List;
import java.util.Objects;

/** Resolves missing timer notices without changing message expiration or chat settings. */
final class TimerNoticeState {
    static final class Change {
        final long hours, time;
        final String by;
        Change(long hours, String by, long time) { this.hours = hours; this.by = by; this.time = time; }
    }
    static boolean needsNotice(Long hours, String by, long time, List<Change> history) {
        if (hours == null || (hours != 0 && hours != 24 && hours != 168 && hours != 2160)) return false;
        if (hours == 0 && (by == null || by.isEmpty() || time <= 0)) return false;
        for (Change event : history) {
            if (event.hours == hours && event.time == time && Objects.equals(event.by, by)) return false;
        }
        return true;
    }
    private TimerNoticeState() { }
}
