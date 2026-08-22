package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Sliding-window rate limiter for Discord/Fluxer-side text triggers.
 *
 * Provides per-author and global limits for {@code /link} and {@code !list}
 * triggers (and any future bot-side commands) to prevent brute-force attacks
 * against the 6-digit account-link code space and to limit denial-of-service
 * potential on the public chat surface.
 *
 * <p>Configuration is read live from {@link ViscordConfigToml.DiscordRateLimit}
 * on every call so {@code /viscord reload} takes effect immediately.
 *
 * <p>A limit of {@code 0} disables the corresponding bucket (treated as
 * "unlimited").
 *
 * <p>Implementation: per-author {@link ArrayDeque} of millisecond timestamps,
 * one global deque, expired timestamps lazily evicted on each call. Entire
 * limiter state is held in memory and never persisted. Idle author entries
 * are evicted opportunistically on {@link #sweep} to keep the map bounded.
 *
 * <p>Thread-safety: all public methods are synchronized on {@code this}.
 * Discord/Fluxer bot threads call concurrently; the synchronized block is
 * short (one map lookup + two deque scans).
 */
public final class DiscordCommandRateLimiter {

    /** Hard cap on tracked authors per bucket (prevents memory exhaustion under attack). */
    private static final int MAX_TRACKED_AUTHORS = 4096;

    /** Sliding window. */
    private static final long WINDOW_MS = 60_000L;

    public enum Command { LINK, LIST }

    private static final class Bucket {
        final Map<String, Deque<Long>> perAuthor = new HashMap<>();
        final Deque<Long> global = new ArrayDeque<>();
    }

    private final Bucket link = new Bucket();
    private final Bucket list = new Bucket();

    /**
     * Test whether the given author may invoke the command right now, and if so
     * record the invocation against the rate-limit window.
     *
     * @param cmd    command kind
     * @param authorId platform-specific author identifier (Discord user ID, Fluxer username)
     * @return {@code true} if the call is allowed; {@code false} if rate-limited
     */
    public synchronized boolean tryConsume(Command cmd, String authorId) {
        Bucket b = bucketFor(cmd);
        int perUser   = perUserLimit(cmd);
        int globalCap = globalLimit(cmd);

        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        // Global cap (0 = unlimited)
        if (globalCap > 0) {
            evict(b.global, cutoff);
            if (b.global.size() >= globalCap) {
                if (ViscordConfigToml.General.DEBUG.get()) {
                    Viscord.LOGGER.info("[Viscord] Rate limit (global): {} attempts/min reached for {}",
                            globalCap, cmd);
                }
                return false;
            }
        }

        // Per-user cap (0 = unlimited)
        if (perUser > 0 && authorId != null && !authorId.isEmpty()) {
            Deque<Long> q = b.perAuthor.get(authorId);
            if (q == null) {
                if (b.perAuthor.size() >= MAX_TRACKED_AUTHORS) {
                    // Under attack: drop oldest tracked author to free a slot.
                    Iterator<Map.Entry<String, Deque<Long>>> it = b.perAuthor.entrySet().iterator();
                    if (it.hasNext()) { it.next(); it.remove(); }
                }
                q = new ArrayDeque<>();
                b.perAuthor.put(authorId, q);
            } else {
                evict(q, cutoff);
            }
            if (q.size() >= perUser) {
                if (ViscordConfigToml.General.DEBUG.get()) {
                    Viscord.LOGGER.info("[Viscord] Rate limit (per-user): {} attempts/min reached for {} by {}",
                            perUser, cmd, authorId);
                }
                return false;
            }
            q.addLast(now);
        }

        if (globalCap > 0) {
            b.global.addLast(now);
        }
        return true;
    }

    /** Drop entries older than the cutoff from the head of the deque. */
    private static void evict(Deque<Long> q, long cutoff) {
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
    }

    /** Periodic sweep: drop empty per-author deques and ones with no fresh entries. */
    public synchronized void sweep() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        for (Bucket b : new Bucket[]{link, list}) {
            evict(b.global, cutoff);
            Iterator<Map.Entry<String, Deque<Long>>> it = b.perAuthor.entrySet().iterator();
            while (it.hasNext()) {
                Deque<Long> q = it.next().getValue();
                evict(q, cutoff);
                if (q.isEmpty()) it.remove();
            }
        }
    }

    private Bucket bucketFor(Command cmd) {
        return cmd == Command.LINK ? link : list;
    }

    private static int perUserLimit(Command cmd) {
        return cmd == Command.LINK
                ? ViscordConfigToml.DiscordRateLimit.LINK_PER_USER_PER_MIN.get()
                : ViscordConfigToml.DiscordRateLimit.LIST_PER_USER_PER_MIN.get();
    }

    private static int globalLimit(Command cmd) {
        return cmd == Command.LINK
                ? ViscordConfigToml.DiscordRateLimit.LINK_GLOBAL_PER_MIN.get()
                : ViscordConfigToml.DiscordRateLimit.LIST_GLOBAL_PER_MIN.get();
    }
}
