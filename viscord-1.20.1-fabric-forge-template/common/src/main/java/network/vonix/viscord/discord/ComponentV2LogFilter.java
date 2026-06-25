package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Log4j 2 filter that suppresses the WARN + stacktrace flood produced by the
 * shaded Javacord 3.8.0 when it encounters Discord Components V2 message types
 * (TextDisplay=10, Container=17, Section=9, Separator=14, Thumbnail=11, MediaGallery=12, File=13).
 *
 * <p>Javacord 3.8.0 (Sept 2023) predates Components V2 (rolled out by Discord in 2024).
 * Its {@code ActionRowImpl.<init>(JsonNode)} throws {@code IllegalStateException} on any
 * unknown child component type, which {@code PacketHandler.lambda$handlePacket$0} catches
 * and re-logs as {@code "Couldn't handle packet of type {} …"} with the full stacktrace.
 *
 * <p>Every giveaway / ticket / economy bot message edit in the guild produced a
 * 12-line stacktrace on the MC server console — observed on Vonix.Network's
 * Otherworld server, 17 crash-report files in a 4-hour window on 2026-06-24.
 *
 * <p>The filter:
 * <ul>
 *   <li>Targets the four shaded Javacord loggers most likely to surface the noise.</li>
 *   <li>Matches by message-format prefix so it only suppresses the V2-related messages,
 *       not all WARN/ERROR from those loggers.</li>
 *   <li>For events in our watched channels (chat + event), emits one clean DEBUG
 *       line per drop with the channel id, so operators flipping to DEBUG can still
 *       see when a watched message was V2-shaped.</li>
 *   <li>Is idempotent — {@link #install(Set)} may be called more than once to swap
 *       the watched-channel set on config reload.</li>
 * </ul>
 *
 * <p>Jackson's {@code JsonNode} is also shaded under {@code network.vonix.viscord.shadow.jackson},
 * so this filter avoids any compile-time Jackson dependency and uses reflection to extract
 * the {@code channel_id} field.
 *
 * <p>Long-term fix: migrate off Javacord to JDA 5.x (full Components V2 support).
 * This filter is technical debt and should die with the migration.
 */
public final class ComponentV2LogFilter extends AbstractFilter {

    // The shaded prefix is set by architectury's transformProductionForge step.
    // Verified against the production jar viscord-forge-4.2.0+mc1.20.1.jar:
    //   $ unzip -l viscord-forge-4.2.0+mc1.20.1.jar | grep javacord
    //   network/vonix/viscord/shadow/javacord/...
    private static final String SHADOW_PREFIX =
            "network.vonix.viscord.shadow.javacord.core";

    private static final String[] TARGET_LOGGERS = {
            SHADOW_PREFIX + ".util.gateway.PacketHandler",
            SHADOW_PREFIX + ".entity.message.component.ActionRowImpl",
            SHADOW_PREFIX + ".entity.message.MessageImpl",
            SHADOW_PREFIX + ".DiscordApiImpl"
    };

    private static final String PACKET_PREFIX    = "Couldn't handle packet of type";
    private static final String COMPONENT_PREFIX = "Couldn't parse the component of type";

    private static final AtomicReference<Set<String>> WATCHED = new AtomicReference<>(Set.of());
    private static volatile boolean installed = false;

    private ComponentV2LogFilter() {
        super(Result.DENY, Result.NEUTRAL);
    }

    /**
     * Install the filter on the shaded Javacord loggers. Idempotent: subsequent
     * calls only update the watched-channel set.
     *
     * @param watchedChannelIds Channel ids whose dropped V2 messages should produce
     *                          a DEBUG breadcrumb. Pass an empty set to suppress
     *                          all V2 noise silently.
     */
    public static synchronized void install(Set<String> watchedChannelIds) {
        WATCHED.set(Set.copyOf(watchedChannelIds == null ? Set.of() : watchedChannelIds));
        if (installed) return;
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration cfg = ctx.getConfiguration();
            ComponentV2LogFilter f = new ComponentV2LogFilter();
            for (String name : TARGET_LOGGERS) {
                LoggerConfig lc = cfg.getLoggerConfig(name);
                if (!name.equals(lc.getName())) {
                    LoggerConfig dedicated = LoggerConfig.createLogger(
                            true, lc.getLevel(), name, "true",
                            new org.apache.logging.log4j.core.config.AppenderRef[0],
                            null, cfg, null);
                    dedicated.addFilter(f);
                    cfg.addLogger(name, dedicated);
                } else {
                    lc.addFilter(f);
                }
            }
            ctx.updateLoggers();
            installed = true;
            Viscord.LOGGER.info("[Viscord] ComponentV2LogFilter installed on {} loggers ({} watched channel(s))",
                    TARGET_LOGGERS.length, WATCHED.get().size());
        } catch (Throwable t) {
            Viscord.LOGGER.warn("[Viscord] Could not install ComponentV2LogFilter: {}", t.getMessage());
        }
    }

    @Override
    public Result filter(LogEvent e) {
        if (e == null || e.getMessage() == null) return Result.NEUTRAL;
        return decide(e.getMessage().getFormat(), e.getMessage().getParameters());
    }

    @Override
    public Result filter(Logger l, Level lv, Marker m, Message msg, Throwable t) {
        return msg == null ? Result.NEUTRAL : decide(msg.getFormat(), msg.getParameters());
    }

    @Override
    public Result filter(Logger l, Level lv, Marker m, String msg, Object... p) {
        return decide(msg, p);
    }

    @Override
    public Result filter(Logger l, Level lv, Marker m, String msg, Object p0) {
        return decide(msg, new Object[]{p0});
    }

    @Override
    public Result filter(Logger l, Level lv, Marker m, String msg, Object p0, Object p1) {
        return decide(msg, new Object[]{p0, p1});
    }

    @Override
    public Result filter(Logger l, Level lv, Marker m, String msg, Object p0, Object p1, Object p2) {
        return decide(msg, new Object[]{p0, p1, p2});
    }

    @Override
    public Result filter(Logger l, Level lv, Marker m, Object msg, Throwable t) {
        return decide(msg == null ? null : msg.toString(), null);
    }

    private Result decide(String format, Object[] params) {
        if (format == null) return Result.NEUTRAL;
        if (!format.startsWith(PACKET_PREFIX) && !format.startsWith(COMPONENT_PREFIX)) {
            return Result.NEUTRAL;
        }
        String channelId = extractChannelId(params);
        if (channelId != null && WATCHED.get().contains(channelId)) {
            // Watched: still DENY the stacktrace, but emit ONE clean DEBUG line
            // for operators. Use Viscord's own logger (not the shaded one) to
            // avoid filter recursion.
            Viscord.LOGGER.debug("[Viscord] Dropped upstream V2 message in watched channel {} ({}).",
                    channelId, format.startsWith(COMPONENT_PREFIX) ? "component" : "packet");
        }
        return Result.DENY;
    }

    /**
     * Extract a {@code channel_id} string from a Log4j parameter array without
     * a compile-time dependency on Jackson. Jackson is shaded under
     * {@code network.vonix.viscord.shadow.jackson}, so we identify it by
     * class-name substring and call {@code get("channel_id")} reflectively.
     */
    private static String extractChannelId(Object[] params) {
        if (params == null) return null;
        for (Object p : params) {
            if (p == null) continue;
            String cn = p.getClass().getName();
            if (cn.contains("jackson") && cn.contains("JsonNode")) {
                try {
                    Method get = p.getClass().getMethod("get", String.class);
                    Object cid = get.invoke(p, "channel_id");
                    if (cid != null) {
                        Method isNull = cid.getClass().getMethod("isNull");
                        Object isNullV = isNull.invoke(cid);
                        if (Boolean.FALSE.equals(isNullV)) {
                            Method asText = cid.getClass().getMethod("asText");
                            Object t = asText.invoke(cid);
                            if (t instanceof String s && !s.isEmpty()) return s;
                        }
                    }
                } catch (Throwable ignored) {
                    // Fall through to toString grep below.
                }
                // Defensive grep on toString — JsonNode's toString is canonical JSON.
                String s = p.toString();
                int i = s.indexOf("\"channel_id\":\"");
                if (i >= 0) {
                    int a = i + 14;
                    int b = s.indexOf('"', a);
                    if (b > a) return s.substring(a, b);
                }
            }
        }
        return null;
    }
}
