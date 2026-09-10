package network.vonix.viscord;

import network.vonix.viscord.config.toml.TomlConfigManager;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.discord.DiscordManager;
import network.vonix.viscord.platform.PlatformEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Viscord {
    public static final String MOD_ID = "viscord";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static Viscord instance;
    private volatile boolean discordEnabled;
    private volatile boolean shuttingDown;

    private static final long SHUTDOWN_GRACE_MS = 2500L;

    public static final ScheduledThreadPoolExecutor ASYNC_EXECUTOR = buildExecutor();

    private static ScheduledThreadPoolExecutor buildExecutor() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        AtomicLong counter = new AtomicLong();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(cores, runnable -> {
            Thread thread = new Thread(runnable, "Viscord-Async-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.setMaximumPoolSize(Math.max(8, cores * 2));
        executor.setKeepAliveTime(30, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        executor.setRemoveOnCancelPolicy(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    public static void executeAsync(Runnable runnable) {
        ASYNC_EXECUTOR.submit(runnable);
    }

    public static void scheduleAsync(Runnable runnable, long delayMs) {
        ASYNC_EXECUTOR.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
    }

    public static void init() {
        instance = new Viscord();
        instance.onInitialize();
    }

    public static Viscord getInstance() {
        return instance;
    }

    public static boolean isShuttingDown() {
        Viscord current = instance;
        return current != null && current.shuttingDown;
    }

    private void onInitialize() {
        Path configDirectory = PlatformEvents.Holder.get().configDirectory().resolve("viscord");
        try {
            Files.createDirectories(configDirectory);
        } catch (Exception e) {
            LOGGER.error("[Viscord] config directory failed", e);
        }
        TomlConfigManager.load(configDirectory);
        PlatformEvents.Holder.get().register(new PlatformEvents.Callbacks(
                DiscordEventHandler::registerCommands,
                this::onServerStarted,
                this::onServerStopping,
                DiscordEventHandler::onPlayerJoin,
                DiscordEventHandler::onPlayerQuit,
                DiscordEventHandler::onLivingDeath));
    }

    private void onServerStarted(net.minecraft.server.MinecraftServer server) {
        shuttingDown = false;
        if (!ViscordConfigToml.General.ENABLED.get()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                DiscordManager.getInstance().initialize(server);
                discordEnabled = true;
            } catch (Exception e) {
                LOGGER.error("[Viscord] initialization failed", e);
            }
        }, ASYNC_EXECUTOR);
    }

    private void onServerStopping(net.minecraft.server.MinecraftServer server) {
        shuttingDown = true;
        if (!discordEnabled) {
            ASYNC_EXECUTOR.shutdown();
            return;
        }

        LOGGER.info("[Viscord] Server stopping; delaying Discord disconnect by {} ms", SHUTDOWN_GRACE_MS);
        scheduleAsync(() -> {
            try {
                DiscordManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("[Viscord] Error during delayed Discord shutdown", e);
            } finally {
                discordEnabled = false;
                ASYNC_EXECUTOR.shutdown();
            }
        }, SHUTDOWN_GRACE_MS);
    }
}
