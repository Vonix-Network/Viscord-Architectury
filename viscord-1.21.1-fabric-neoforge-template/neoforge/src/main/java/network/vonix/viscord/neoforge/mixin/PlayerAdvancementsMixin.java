package network.vonix.viscord.neoforge.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Mixin to intercept advancement awards and send them to Discord.
 * 1.21.1 API uses AdvancementHolder instead of Advancement.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    /**
     * Intercept advancement award to send to Discord.
     * Targets the award method which is called when a player earns an advancement.
     */
    @Inject(method = "award", at = @At("RETURN"))
    private void viscord$onAdvancementAward(AdvancementHolder advancementHolder, String criterionName,
            CallbackInfoReturnable<Boolean> cir) {
        // Only process if the advancement was actually awarded (returned true)
        if (!cir.getReturnValue()) {
            return;
        }

        // CRITICAL: Check if the advancement is actually COMPLETED, not just updated
        // This prevents triggering on partial progress (e.g., getting one netherite armor piece
        // out of four needed for "Cover me in debris" achievement)
        AdvancementProgress progress = ((PlayerAdvancements)(Object)this).getOrStartProgress(advancementHolder);
        if (!progress.isDone()) {
            Viscord.LOGGER.debug("[Discord] Advancement not yet completed, skipping: {}", advancementHolder.id());
            return;
        }

        Viscord.LOGGER.debug("[Discord] Advancement awarded: {}", advancementHolder.id());

        // Check if Discord integration is running
        if (!DiscordManager.getInstance().isRunning()) {
            Viscord.LOGGER.debug("[Discord] Not sending advancement - Discord not running");
            return;
        }

        // Check if advancement notifications are enabled
        if (!ViscordConfig.CONFIG.sendAdvancement.get()) {
            Viscord.LOGGER.debug("[Discord] Not sending advancement - disabled in config");
            return;
        }

        // Get advancement display info (1.21.1 uses Optional)
        Optional<DisplayInfo> displayOpt = advancementHolder.value().display();
        if (displayOpt.isEmpty()) {
            return; // Hidden advancement (no display)
        }

        DisplayInfo display = displayOpt.get();

        // Only send if advancement should announce to chat
        if (!display.shouldAnnounceChat()) {
            return;
        }

        try {
            String username = player.getName().getString();
            String advancementTitle = display.getTitle().getString();
            String advancementDescription = display.getDescription().getString();

            Viscord.LOGGER.info("[Discord] Sending advancement to Discord: {} - {}", username, advancementTitle);

            DiscordManager.getInstance().sendAdvancementEmbed(
                    username,
                    advancementTitle,
                    advancementDescription);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Failed to send advancement", e);
        }
    }
}
