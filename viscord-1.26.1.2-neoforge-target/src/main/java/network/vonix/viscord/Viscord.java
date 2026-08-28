package network.vonix.viscord;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import network.vonix.viscord.config.toml.TomlConfigManager;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.file.Path;
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


    public static synchronized void init() {
        if (instance != null) {
            LOGGER.debug("[{}] Viscord.init() already completed", MOD_ID);
            return;
        }
        LOGGER.info("[{}] Viscord.init() called!", MOD_ID);
        instance = new Viscord();
        instance.onInitialize();
        LOGGER.info("[{}] Viscord.init() completed!", MOD_ID);
    }

    public static Viscord getInstance() {
        return instance;
    }

    /**
     * Returns Viscord's config directory under the NeoForge game config path.
     */
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
    }

    private void onInitialize() {
        LOGGER.info("[{}] Initializing Viscord (Standalone Discord Integration)", MOD_ID);

        // Load TOML config from config/viscord/ subdirectory
        Path configDir = getConfigDirectory();
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

        // NeoForge owns lifecycle/event registration in the platform entry point.
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!ViscordConfigToml.General.ENABLED.get()) {
            LOGGER.info("[{}] Discord integration disabled in config", MOD_ID);
            return;
        }
        Viscord current = Viscord.getInstance();
        if (current == null) {
            LOGGER.error("[{}] Server started before Viscord initialization", MOD_ID);
            return;
        }
        ASYNC_EXECUTOR.execute(() -> {
            try {
                DiscordManager.getInstance().initialize(server);
                current.discordEnabled = DiscordManager.getInstance().isRunning();
                LOGGER.info("[{}] Discord module enabled", MOD_ID);
            } catch (Exception e) {
                LOGGER.error("[{}] Failed to initialize Discord", MOD_ID, e);
            }
        });
        LOGGER.info("[{}] Discord initialization started asynchronously", MOD_ID);
    }

    public static void onServerStopping() {
            // Run shutdown off the server-stopping thread so the tick loop is
            // not blocked while network futures complete. We give it up to
            // 5 seconds total (Discord + Fluxer + webhook clients) before
            // forcibly tearing down the executor.
            Viscord current = Viscord.getInstance();
            if (current == null) {
                ASYNC_EXECUTOR.shutdown();
                return;
            }
            java.util.concurrent.CompletableFuture
                .runAsync(() -> {
                    try {
                        // Do not construct DiscordManager during shutdown when the
                        // integration never initialized; construction creates OkHttp
                        // clients and can trigger packaged dependency linkage failures.
                        if (current.discordEnabled) {
                            DiscordManager.getInstance().shutdown();
                        }
                        current.discordEnabled = false;
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

    public static void onPlayerJoin(Entity entity) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player
                && DiscordManager.getInstance().isRunning()
                && ViscordConfigToml.Messages.Events.JOIN.get()) {
            DiscordManager.getInstance().sendJoinEmbed(player.getName().getString(), player.getUUID().toString());
        }
    }

    public static void onPlayerQuit(Entity entity) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player
                && DiscordManager.getInstance().isRunning()
                && ViscordConfigToml.Messages.Events.LEAVE.get()) {
            DiscordManager.getInstance().sendLeaveEmbed(player.getName().getString(), player.getUUID().toString());
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && ViscordConfigToml.Messages.Events.DEATH.get()
                && DiscordManager.getInstance().isRunning()) {
            DiscordManager.getInstance().sendDeathEmbed(event.getSource().getLocalizedDeathMessage(player).getString());
        }
    }
}
