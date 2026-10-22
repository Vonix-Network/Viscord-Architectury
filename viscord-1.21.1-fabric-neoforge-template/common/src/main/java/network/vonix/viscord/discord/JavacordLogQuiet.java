package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Silences Javacord 3.8's Components V2 noise (Container=17, TextDisplay=10, Section,
 * Separator, Thumbnail, MediaGallery, File…) which Javacord cannot parse and therefore
 * reports on every affected gateway message.
 *
 * <p>This deliberately touches log4j only through the configuration API
 * ({@link Configurator#setLevel(String, Level)}). It must never subclass log4j-core
 * types such as {@code AbstractFilter}: on Forge 1.20.1 log4j lives in the
 * {@code MC-BOOTSTRAP} module layer, and a mod-loader class whose supertypes or
 * interface signatures reference log4j-core types (for example {@code Filter}'s
 * {@code MessageSupplier} overloads) can poison loader constraints, turning any
 * exception logged by {@code EventBus.handleException} into
 * {@code LinkageError: loader constraint violation} and killing the server.
 */
public final class JavacordLogQuiet {

    /** Relocated Javacord loggers that emit the V2 packet noise. */
    private static final String[] NOISY_LOGGERS = new String[] {
            "network.vonix.viscord.shadow.javacord.core.util.gateway.PacketHandler",
            "network.vonix.viscord.shadow.javacord.core.entity.message.component.ActionRowImpl",
            "network.vonix.viscord.shadow.javacord.core.entity.message.MessageImpl",
            "network.vonix.viscord.shadow.javacord.core.DiscordApiImpl"
    };

    private static volatile boolean installed = false;

    private JavacordLogQuiet() {
    }

    /**
     * Raises the level of the noisy Javacord loggers to {@code ERROR}. Idempotent.
     *
     * <p>Real errors from those loggers still surface; only the per-message WARN/DEBUG
     * chatter is dropped. Failure is non-fatal: if log4j cannot be reconfigured the
     * warnings stay visible and Discord keeps working.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            for (String name : NOISY_LOGGERS) {
                Configurator.setLevel(name, Level.ERROR);
            }
            installed = true;
            Viscord.LOGGER.info("[Viscord] Javacord Components-V2 noise suppressed on {} logger(s).",
                    NOISY_LOGGERS.length);
        } catch (Throwable t) {
            Viscord.LOGGER.warn("[Viscord] Could not quiet Javacord loggers: {} — V2 packet warnings will remain visible.",
                    t.toString());
        }
    }
}
