package ca.vicilearning.dashboard.domain;

import java.time.ZoneId;

// Vici's actual timezone. SimplyBook.me booking times come in as plain local strings with no
// zone info, and they're already Pacific, so "now" has to be computed here too or date math
// against bookings drifts by the UTC offset. Using the zone id (not a fixed -7/-8) so DST
// just works.
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("America/Vancouver");

    private AppClock() {}
}
