package network.vonix.viscord;

import dev.architectury.event.events.common.LifecycleEvent;
import network.vonix.viscord.config.toml.TomlConfigManager;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;
import network.vonix.viscord.discord.DiscordEventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Viscord {
    public static final String MOD_ID = "viscord";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static Viscord instance;
    private volatile boolean discordEnabled = false;
    public static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool();

    public static void executeAsync(Runnable runnable) {
        ASYNC_EXECUTOR.submit(runnable);
    }


    public static void init() {
        LOGGER.info("[{}] Viscord.init() called!", MOD_ID);
        instance = new Viscord();
        instance.onInitialize();
        LOGGER.info("[{}] Viscord.init() completed!", MOD_ID);
    }

    public static Viscord getInstance() {
        return instance;
    }

    private void onInitialize() {
        LOGGER.info("[{}] Initializing Viscord (Standalone Discord Integration)", MOD_ID);

        // Load TOML config from config/viscord/ subdirectory
        Path configDir = dev.architectury.platform.Platform.getConfigFolder().resolve("viscord");
        Path tomlConfigPath = configDir.resolve("viscord.toml");
        
        // Ensure config directory exists
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
        }
        
        LOGGER.info("[{}] Config directory: {}", MOD_ID, configDir.toAbsolutePath());
        LOGGER.info("[{}] Config file path: {}", MOD_ID, tomlConfigPath.toAbsolutePath());
        
        // Load TOML config (with auto-migration from JSON if needed)
        TomlConfigManager.load(configDir);
        
        if (tomlConfigPath.toFile().exists()) {
            LOGGER.info("[{}] TOML config loaded successfully", MOD_ID);
        } else {
            LOGGER.error("[{}] TOML config file still does not exist after load attempt!", MOD_ID);
        }

        // Register Discord events
        DiscordEventHandler.register();

        LifecycleEvent.SERVER_STARTED.register(server -> {
            if (ViscordConfigToml.General.ENABLED.get()) {
                // Non-blocking async initialization
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        DiscordManager.getInstance().initialize(server);
                        discordEnabled = true;
                        LOGGER.info("[{}] Discord module enabled", MOD_ID);
                    } catch (Exception e) {
                        LOGGER.error("[{}] Failed to initialize Discord: {}", MOD_ID, e.getMessage());
                    }
                }, ASYNC_EXECUTOR);
                LOGGER.info("[{}] Discord initialization started asynchronously", MOD_ID);
            }
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            if (discordEnabled) {
                try {
                    DiscordManager.getInstance().shutdown();
                    LOGGER.debug("[{}] Discord shutdown complete", MOD_ID);
                } catch (Exception e) {
                    LOGGER.error("[{}] Error during Discord shutdown", MOD_ID, e);
                }
            }
            ASYNC_EXECUTOR.shutdown();
            try {
                if (!ASYNC_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS))
                    LOGGER.warn("[{}] Async executor did not terminate in time", MOD_ID);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
