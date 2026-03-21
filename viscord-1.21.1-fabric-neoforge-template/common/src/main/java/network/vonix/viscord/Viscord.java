package network.vonix.viscord;

import dev.architectury.event.events.common.LifecycleEvent;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.config.simple.SimpleConfigManager;
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
    private boolean discordEnabled = false;
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

        // Load Discord config
        Path configDir = dev.architectury.platform.Platform.getConfigFolder();
        Path discordConfigPath = configDir.resolve("viscord.json");
        
        LOGGER.info("[{}] Config directory: {}", MOD_ID, configDir.toAbsolutePath());
        LOGGER.info("[{}] Config file path: {}", MOD_ID, discordConfigPath.toAbsolutePath());
        LOGGER.info("[{}] Config spec has {} values", MOD_ID, ViscordConfig.SPEC.getValues().size());
        
        SimpleConfigManager.load(discordConfigPath, ViscordConfig.SPEC);
        
        if (discordConfigPath.toFile().exists()) {
            LOGGER.info("[{}] Config file exists and was loaded successfully", MOD_ID);
        } else {
            LOGGER.error("[{}] Config file still does not exist after load attempt!", MOD_ID);
        }

        // Register Discord events
        DiscordEventHandler.register();

        LifecycleEvent.SERVER_STARTED.register(server -> {
            if (ViscordConfig.CONFIG.enabled.get()) {
                try {
                    java.util.concurrent.CompletableFuture<Void> discordInitFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
                        DiscordManager.getInstance().initialize(server);
                    });
                    
                    // Wait max 10 seconds for Discord initialization
                    discordInitFuture.get(10, TimeUnit.SECONDS);
                    discordEnabled = true;
                    LOGGER.info("[{}] Discord module enabled", MOD_ID);
                } catch (java.util.concurrent.TimeoutException e) {
                    LOGGER.error("[{}] Discord initialization timed out after 10 seconds!", MOD_ID);
                } catch (Exception e) {
                    LOGGER.error("[{}] Failed to initialize Discord: {}", MOD_ID, e.getMessage());
                }
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
        });
    }
}
