package network.vonix.viscord.neoforge.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept advancement awards and send them to Discord/Fluxer.
 * Only fires when the advancement is FULLY completed (all criteria done).
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award", at = @At("RETURN"))
    private void viscord$onAdvancementAward(AdvancementHolder advancementHolder, String criterionName,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!DiscordManager.getInstance().isRunning()) return;
        if (!ViscordConfigToml.Messages.Events.ADVANCEMENT.get()) return;

        DisplayInfo display = advancementHolder.value().display().orElse(null);
        if (display == null) return;
        if (!display.shouldAnnounceChat()) return;

        // Only notify when ALL criteria are satisfied — not on partial progress
        try {
            AdvancementProgress progress = getOrStartProgress(advancementHolder);
            if (!progress.isDone()) return;
        } catch (Exception e) {
            return; // Can't determine progress — skip to be safe
        }

        try {
            DiscordManager.getInstance().sendAdvancementEmbed(
                    player.getName().getString(),
                    display.getTitle().getString(),
                    display.getDescription().getString());
        } catch (Exception e) {
            // Silently fail — don't break advancement system
        }
    }
}
