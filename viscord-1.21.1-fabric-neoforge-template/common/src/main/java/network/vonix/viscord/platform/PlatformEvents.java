package network.vonix.viscord.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Loader-neutral callbacks supplied by Fabric and NeoForge modules. */
public interface PlatformEvents {
    void register(Callbacks callbacks);
    Path configDirectory();
    record Callbacks(Consumer<CommandDispatcher<CommandSourceStack>> commands,
                     Consumer<MinecraftServer> serverStarted,
                     Consumer<MinecraftServer> serverStopping,
                     Consumer<ServerPlayer> playerJoin,
                     Consumer<ServerPlayer> playerQuit,
                     BiConsumer<LivingEntity, DamageSource> livingDeath) {}
    final class Holder {
        private static PlatformEvents instance;
        private Holder() {}
        public static void install(PlatformEvents value) { instance = value; }
        public static PlatformEvents get() {
            if (instance == null) throw new IllegalStateException("Viscord loader platform was not installed");
            return instance;
        }
    }
}
