package network.vonix.viscord.discord;

import com.fasterxml.jackson.databind.JsonNode;
import network.vonix.viscord.Viscord;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Log4j filter that suppresses Javacord 3.8.0's noisy "Couldn't handle packet" /
 * "Couldn't parse the component" warnings caused by Discord Components V2
 * (Container=17, TextDisplay=10, Section, Separator, Thumbnail, MediaGallery, File…)
 * which Javacord does not understand.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If the offending packet's {@code channel_id} is NOT in the watched set →
 *       DENY silently (no console output).</li>
 *   <li>If it IS a watched channel → DENY the raw warning + emit one clean
 *       {@code Viscord.LOGGER.debug} so devs know V2 content was dropped from a
 *       channel that matters (until JDA migration / V2 parser lands).</li>
 *   <li>All other log events on those Javacord loggers pass through unchanged.</li>
 * </ul>
 *
 * <p>The filter is registered on the relocated Javacord
 * {@code PacketHandler} and {@code ActionRowImpl} loggers, plus a fallback root
 * filter keyed by message prefix for any logger the relocator missed.
 */
public final class ComponentV2LogFilter extends AbstractFilter {

    /** Fully-qualified relocated logger names that emit Components V2 noise. */
    private static final String[] TARGET_LOGGERS = new String[] {
            "network.vonix.viscord.shadow.javacord.core.util.gateway.PacketHandler",
            "network.vonix.viscord.shadow.javacord.core.entity.message.component.ActionRowImpl",
            "network.vonix.viscord.shadow.javacord.core.entity.message.MessageImpl",
            "network.vonix.viscord.shadow.javacord.core.DiscordApiImpl"
    };

    private static final String PACKET_PREFIX     = "Couldn't handle packet of type";
    private static final String COMPONENT_PREFIX  = "Couldn't parse the component of type";

    /** Single shared instance; channel set is swapped atomically. */
    private static final AtomicReference<Set<String>> WATCHED = new AtomicReference<>(Collections.emptySet());
    private static volatile boolean installed = false;

    private ComponentV2LogFilter() {
        super(Result.DENY, Result.NEUTRAL);
    }

    /**
     * Install the filter on the relocated Javacord loggers. Idempotent —
     * subsequent calls only refresh the watched-channel set.
     *
     * @param watchedChannelIds channel IDs whose V2-drops should be reported as
     *                          a single DEBUG line (vs silently swallowed)
     */
    public static synchronized void install(Set<String> watchedChannelIds) {
        Set<String> snapshot = new HashSet<>();
        if (watchedChannelIds != null) {
            for (String id : watchedChannelIds) {
                if (id != null && !id.isEmpty()) snapshot.add(id);
            }
        }
        WATCHED.set(Collections.unmodifiableSet(snapshot));

        if (installed) {
            Viscord.LOGGER.debug("[Viscord] ComponentV2LogFilter refreshed; watched channels: {}", snapshot);
            return;
        }

        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration cfg = ctx.getConfiguration();
            ComponentV2LogFilter filter = new ComponentV2LogFilter();

            for (String name : TARGET_LOGGERS) {
                LoggerConfig lc = cfg.getLoggerConfig(name);
                // If no specific config exists, create one inheriting from root so we don't
                // pollute other loggers' filter chains.
                if (!name.equals(lc.getName())) {
                    LoggerConfig dedicated = LoggerConfig.createLogger(
                            true, lc.getLevel(), name, "true",
                            new org.apache.logging.log4j.core.config.AppenderRef[0],
                            null, cfg, null);
                    dedicated.addFilter(filter);
                    cfg.addLogger(name, dedicated);
                } else {
                    lc.addFilter(filter);
                }
            }
            ctx.updateLoggers();
            installed = true;
            Viscord.LOGGER.info("[Viscord] ComponentV2LogFilter installed on {} Javacord logger(s); watched channels: {}",
                    TARGET_LOGGERS.length, snapshot);
        } catch (Throwable t) {
            Viscord.LOGGER.warn("[Viscord] Failed to install ComponentV2LogFilter: {} — V2 packet warnings will remain noisy.",
                    t.toString());
        }
    }

    // ---------------------------------------------------------------------
    // Filter callbacks (Log4j calls one of these depending on log site shape)
    // ---------------------------------------------------------------------

    @Override
    public Result filter(LogEvent event) {
        if (event == null || event.getMessage() == null) return Result.NEUTRAL;
        return decide(event.getMessage().getFormat(), event.getMessage().getParameters());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (msg == null) return Result.NEUTRAL;
        return decide(msg.getFormat(), msg.getParameters());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return decide(msg, params);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return decide(msg == null ? null : msg.toString(), null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object p0) {
        return decide(msg, new Object[]{p0});
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object p0, Object p1) {
        return decide(msg, new Object[]{p0, p1});
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object p0, Object p1, Object p2) {
        return decide(msg, new Object[]{p0, p1, p2});
    }

    // ---------------------------------------------------------------------
    // Core logic
    // ---------------------------------------------------------------------

    private Result decide(String format, Object[] params) {
        if (format == null) return Result.NEUTRAL;
        boolean isV2Noise = format.startsWith(PACKET_PREFIX) || format.startsWith(COMPONENT_PREFIX);
        if (!isV2Noise) return Result.NEUTRAL;

        String channelId = extractChannelId(params);
        Set<String> watched = WATCHED.get();

        if (channelId != null && watched.contains(channelId)) {
            // Watched channel: surface a single clean DEBUG line.
            String packetType = extractPacketType(format, params);
            Viscord.LOGGER.debug("[Viscord] Dropped Discord Components V2 message in watched channel {} (type {}). "
                            + "Full V2 rendering requires Javacord -> JDA migration.",
                    channelId, packetType);
        }
        // Unwatched OR watched: always deny the raw stacktrace.
        return Result.DENY;
    }

    /** Try to pull {@code channel_id} out of any JsonNode parameter Javacord logged. */
    private static String extractChannelId(Object[] params) {
        if (params == null) return null;
        for (Object p : params) {
            if (p instanceof JsonNode) {
                JsonNode node = (JsonNode) p;
                JsonNode cid = node.get("channel_id");
                if (cid != null && !cid.isNull()) return cid.asText();
            } else if (p != null && p.getClass().getName().startsWith("com.fasterxml.jackson.databind.")) {
                // Relocated/shaded Jackson — fall back to toString grep.
                String s = p.toString();
                int i = s.indexOf("\"channel_id\":\"");
                if (i >= 0) {
                    int start = i + 14;
                    int end = s.indexOf('"', start);
                    if (end > start) return s.substring(start, end);
                }
            }
        }
        return null;
    }

    /** "Couldn't handle packet of type {}" → first param; component variant → embed in format string. */
    private static String extractPacketType(String format, Object[] params) {
        if (format.startsWith(COMPONENT_PREFIX)) return "component";
        if (params != null && params.length > 0 && params[0] != null) return params[0].toString();
        return "unknown";
    }
}
