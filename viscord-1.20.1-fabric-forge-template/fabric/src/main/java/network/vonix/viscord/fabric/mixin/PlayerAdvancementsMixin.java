package network.vonix.viscord.fabric.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept advancement awards and send them to Discord.
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
    private void viscord$onAdvancementAward(Advancement advancement, String criterionName,
            CallbackInfoReturnable<Boolean> cir) {
        // Only process if the advancement was actually awarded (returned true)
        if (!cir.getReturnValue()) {
            return;
        }

        // Check if Discord integration is running
        if (!DiscordManager.getInstance().isRunning()) {
            return;
        }

        // Check if advancement notifications are enabled
        if (!ViscordConfig.CONFIG.sendAdvancement.get()) {
            return;
        }

        // Get advancement display info
        DisplayInfo display = advancement.getDisplay();
        if (display == null) {
            return; // Hidden advancement (no display)
        }

        // Only send if advancement should announce to chat
        if (!display.shouldAnnounceChat()) {
            return;
        }

        try {
            String username = player.getName().getString();
            String advancementTitle = display.getTitle().getString();
            String advancementDescription = display.getDescription().getString();

            DiscordManager.getInstance().sendAdvancementEmbed(
                    username,
                    advancementTitle,
                    advancementDescription);
        } catch (Exception e) {
            // Silently fail - don't break advancement system
        }
    }
}
