package network.vonix.viscord;

import network.vonix.viscord.config.toml.TomlConfigManager;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.platform.PlatformEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Viscord {
    public static final String MOD_ID = "viscord";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static Viscord instance;
    private volatile boolean discordEnabled = false;

    /**
     * Bounded, daemon-threaded executor for all Viscord background work.
     * Replaces the previous unbounded cached pool to prevent thread-explosion
     * under network back-pressure.
     */
    public static final ScheduledThreadPoolExecutor ASYNC_EXECUTOR = buildExecutor();

    private static ScheduledThreadPoolExecutor buildExecutor() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        AtomicLong counter = new AtomicLong();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "Viscord-Async-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(cores, tf);
        exec.setMaximumPoolSize(Math.max(8, cores * 2));
        exec.setKeepAliveTime(30, TimeUnit.SECONDS);
        exec.allowCoreThreadTimeOut(true);
        exec.setRemoveOnCancelPolicy(true);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return exec;
    }

    public static void executeAsync(Runnable runnable) {
        ASYNC_EXECUTOR.submit(runnable);
    }

    /**
     * Schedule a task to run after the given delay. Replaces the
     * "submit + Thread.sleep" anti-pattern that previously occupied a pool
     * thread for the full delay.
     */
    public static void scheduleAsync(Runnable runnable, long delayMs) {
        ASYNC_EXECUTOR.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
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
        Path configDir = PlatformEvents.Holder.get().configDirectory().resolve("viscord");
        Path tomlConfigPath = configDir.resolve("viscord.toml");
        
        // Ensure config directory exists
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
        }
        
        LOGGER.info("[{}] Config directory: {}", MOD_ID, configDir.toAbsolutePath());
        LOGGER.info("[{}] Config file path: {}", MOD_ID, tomlConfigPath.toAbsolutePath());
        
        // Load TOML config (with auto-migration from JSON if needed)
        TomlConfigManager.load(configDir);

        // TomlConfigManager.load() completes both first-run creation and existing-file loading
        // synchronously. Do not wait on the path here.
        LOGGER.info("[{}] TOML config initialization completed", MOD_ID);

        // Register Discord events
        PlatformEvents.Holder.get().register(new PlatformEvents.Callbacks(
                DiscordEventHandler::registerCommands, this::onServerStarted, this::onServerStopping,
                DiscordEventHandler::onPlayerJoin, DiscordEventHandler::onPlayerQuit, DiscordEventHandler::onLivingDeath));
    }

    private void onServerStarted(net.minecraft.server.MinecraftServer server) {
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
    }

    private void onServerStopping(net.minecraft.server.MinecraftServer server) {
            // Run shutdown off the server-stopping thread so the tick loop is
            // not blocked while network futures complete. We give it up to
            // 5 seconds total (Discord + Fluxer + webhook clients) before
            // forcibly tearing down the executor.
            if (!discordEnabled) {
                ASYNC_EXECUTOR.shutdown();
                return;
            }
            java.util.concurrent.CompletableFuture
                .runAsync(() -> {
                    try {
                        DiscordManager.getInstance().shutdown();
                        LOGGER.debug("[{}] Discord shutdown complete", MOD_ID);
                    } catch (Exception e) {
                        LOGGER.error("[{}] Error during Discord shutdown", MOD_ID, e);
                    }
                }, ASYNC_EXECUTOR)
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((v, t) -> {
                    if (t != null) LOGGER.warn("[{}] Shutdown timed out: {}", MOD_ID, t.getMessage());
                    ASYNC_EXECUTOR.shutdown();
                    try {
                        if (!ASYNC_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                            ASYNC_EXECUTOR.shutdownNow();
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
    }
}
