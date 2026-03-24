package network.vonix.viscord.discord;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.simple.SimpleConfigManager;
import java.nio.file.Path;

/**
 * Minecraft event handler for Discord integration.
 * Handles commands, player join/leave, death, and advancement events.
 */
public class DiscordEventHandler {

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry) -> {
            registerCommands(dispatcher);
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (DiscordManager.getInstance().isRunning()) {
                DiscordManager.getInstance().sendJoinEmbed(player.getName().getString(), player.getUUID().toString());
                // Schedule status update after delay to ensure accurate player count
                DiscordManager.getInstance().scheduleStatusUpdate(1000);
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (DiscordManager.getInstance().isRunning()) {
                DiscordManager.getInstance().sendLeaveEmbed(player.getName().getString(), player.getUUID().toString());
                // Schedule status update after delay to ensure accurate player count
                DiscordManager.getInstance().scheduleStatusUpdate(1000);
            }
        });

        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) entity;
                if (ViscordConfig.CONFIG.sendDeath.get()) {
                    String deathMessage = source.getLocalizedDeathMessage(player).getString();
                    DiscordManager.getInstance().sendDeathEmbed(deathMessage);
                }
            }
            return EventResult.pass();
        });

        // Chat event is handled via ChatFormatter or Mixin to ensure compatibility
        // Advancement event requires Mixin into PlayerAdvancements
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /discord command - show invite link and manage preferences
        dispatcher.register(
                Commands.literal("discord")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> {
                            String invite = ViscordConfig.CONFIG.discordInviteUrl.get();
                            CommandSourceStack source = context.getSource();

                            if (invite == null || invite.isEmpty()) {
                                source.sendSuccess(new TextComponent(
                                        "§cDiscord invite URL is not configured."), false);
                            } else {
                                MutableComponent clickable = new TextComponent("Click Here to join the Discord!")
                                        .withStyle(style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite))
                                                .withUnderlined(true)
                                                .withColor(ChatFormatting.AQUA));
                                source.sendSuccess(clickable, false);
                            }
                            return 1;
                        })
                        .then(Commands.literal("invite")
                                .executes(context -> {
                                    String invite = ViscordConfig.CONFIG.discordInviteUrl.get();
                                    CommandSourceStack source = context.getSource();
        
                                    if (invite == null || invite.isEmpty()) {
                                        source.sendSuccess(new TextComponent(
                                                "§cDiscord invite URL is not configured."), false);
                                    } else {
                                        MutableComponent clickable = new TextComponent("Click Here to join the Discord!")
                                                .withStyle(style -> style
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite))
                                                        .withUnderlined(true)
                                                        .withColor(ChatFormatting.AQUA));
                                        source.sendSuccess(clickable, false);
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("messages")
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerMessagesFiltered(player.getUUID(), false);
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§aCross-server messages enabled!"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerMessagesFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§cCross-server messages disabled!"), false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasServerMessagesFiltered(player.getUUID());
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§7Cross-server messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                            false);
                                    return 1;
                                }))
                        .then(Commands.literal("events")
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setEventsFiltered(player.getUUID(), false);
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§aEvent messages enabled!"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setEventsFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§cEvent messages disabled!"), false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasEventsFiltered(player.getUUID());
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§7Event messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                            false);
                                    return 1;
                                }))
                        .then(Commands.literal("servermessages")
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerSystemMessagesFiltered(player.getUUID(), false);
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§aServer system messages enabled!"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerSystemMessagesFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§cServer system messages disabled!"), false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasServerSystemMessagesFiltered(player.getUUID());
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§7Server system messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                            false);
                                    return 1;
                                })));

        // /viscord reload command - admin only
        dispatcher.register(
                Commands.literal("viscord")
                        .requires(source -> source.hasPermission(4)) // OP only
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§aReloading Viscord configuration..."), false);
                                    
                                    // Run reload async to prevent blocking
                                    Viscord.ASYNC_EXECUTOR.submit(() -> {
                                        try {
                                            // Shutdown current connection
                                            if (DiscordManager.getInstance().isRunning()) {
                                                DiscordManager.getInstance().shutdown();
                                                Thread.sleep(1000); // Wait for clean shutdown
                                            }
                                            
                                            // Reload config
                                            Path configPath = dev.architectury.platform.Platform.getConfigFolder()
                                                    .resolve("viscord").resolve("viscord.json");
                                            SimpleConfigManager.load(configPath, ViscordConfig.SPEC);
                                            
                                            // Re-initialize if enabled
                                            if (ViscordConfig.CONFIG.enabled.get()) {
                                                DiscordManager.getInstance().initialize(
                                                        context.getSource().getServer());
                                                context.getSource().sendSuccess( new TextComponent(
                                                        "§aViscord reloaded successfully!"), false);
                                            } else {
                                                context.getSource().sendSuccess( new TextComponent(
                                                        "§eViscord is disabled in config."), false);
                                            }
                                        } catch (Exception e) {
                                            context.getSource().sendFailure(new TextComponent(
                                                    "§cFailed to reload: " + e.getMessage()));
                                            Viscord.LOGGER.error("[Viscord] Reload failed", e);
                                        }
                                    });
                                    return 1;
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    boolean running = DiscordManager.getInstance().isRunning();
                                    String platform = ViscordConfig.CONFIG.platform.get();
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§6§l=== Viscord Status ===\n" +
                                            "§7Status: " + (running ? "§aRunning" : "§cStopped") + "\n" +
                                            "§7Platform: §b" + platform + "\n" +
                                            "§7Enabled: " + (ViscordConfig.CONFIG.enabled.get() ? "§aYes" : "§cNo")),
                                            false);
                                    return 1;
                                })));

        // /vonix discord commands - rebranded to /viscord with /vonix alias
        dispatcher.register(
                Commands.literal("viscord")
                        .then(Commands.literal("discord")
                                .then(Commands.literal("link")
                                        .executes(context -> {
                                            if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                                                context.getSource().sendFailure(
                                                        new TextComponent("§cAccount linking is disabled."));
                                                return 0;
                                            }

                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String code = DiscordManager.getInstance().generateLinkCode(player);

                                            if (code != null) {
                                                int expiryMinutes = ViscordConfig.CONFIG.linkCodeExpiry.get() / 60;
                                                context.getSource().sendSuccess( new TextComponent(
                                                        "§aYour link code is: §e" + code + "\n" +
                                                                "§7Use §b/link " + code
                                                                + "§7 in Discord to link your account.\n" +
                                                                "§7Code expires in " + expiryMinutes + " minutes."),
                                                        false);
                                                return 1;
                                            } else {
                                                // Provide more specific error messages
                                                DiscordManager discordManager = DiscordManager.getInstance();
                                                if (!discordManager.isRunning()) {
                                                    context.getSource().sendFailure(
                                                            new TextComponent("§cDiscord bot is not connected. Please contact an administrator."));
                                                    Viscord.LOGGER.warn("[Viscord] Link code generation failed - Discord bot is not running");
                                                } else if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                                                    context.getSource().sendFailure(
                                                            new TextComponent("§cAccount linking is disabled in configuration."));
                                                } else {
                                                    context.getSource().sendFailure(
                                                            new TextComponent("§cFailed to generate link code. You may already have an account linked."));
                                                    Viscord.LOGGER.error("[Viscord] Link code generation failed for player: {}", 
                                                        player.getName().getString());
                                                }
                                                return 0;
                                            }
                                        }))
                                .then(Commands.literal("unlink")
                                        .executes(context -> {
                                            if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                                                context.getSource().sendFailure(
                                                        new TextComponent("§cAccount linking is disabled."));
                                                return 0;
                                            }

                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean success = DiscordManager.getInstance()
                                                    .unlinkAccount(player.getUUID());

                                            if (success) {
                                                context.getSource().sendSuccess( new TextComponent(
                                                        "§aYour Discord account has been unlinked."), false);
                                                return 1;
                                            } else {
                                                context.getSource().sendFailure(
                                                        new TextComponent("§cYou don't have a linked Discord account."));
                                                return 0;
                                            }
                                        }))
                                .then(Commands.literal("messages")
                                        .then(Commands.literal("enable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setServerMessagesFiltered(player.getUUID(), false);
                                                    context.getSource().sendSuccess( new TextComponent(
                                                            "§aServer messages enabled!"), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setServerMessagesFiltered(player.getUUID(), true);
                                                    context.getSource().sendSuccess( new TextComponent(
                                                            "§cServer messages disabled!"), false);
                                                    return 1;
                                                }))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean isFiltered = DiscordManager.getInstance()
                                                    .hasServerMessagesFiltered(player.getUUID());
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§7Server messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                                    false);
                                            return 1;
                                        }))
                                .then(Commands.literal("events")
                                        .then(Commands.literal("enable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setEventsFiltered(player.getUUID(), false);
                                                    context.getSource().sendSuccess( new TextComponent(
                                                            "§aEvent messages enabled!"), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setEventsFiltered(player.getUUID(), true);
                                                    context.getSource().sendSuccess( new TextComponent(
                                                            "§cEvent messages disabled!"), false);
                                                    return 1;
                                                }))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean isFiltered = DiscordManager.getInstance()
                                                    .hasEventsFiltered(player.getUUID());
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§7Event messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                                    false);
                                            return 1;
                                        }))
                                .then(Commands.literal("help")
                                        .executes(context -> {
                                            context.getSource().sendSuccess( new TextComponent(
                                                    "§6§l=== Viscord Discord Commands ===\n" +
                                                            "§b/discord§7 - Show Discord invite link\n" +
                                                            "§b/viscord discord link§7 - Generate account link code\n" +
                                                            "§b/viscord discord unlink§7 - Unlink your Discord\n" +
                                                            "§b/viscord discord messages§7 - Toggle server messages\n" +
                                                            "§b/viscord discord events§7 - Toggle event messages\n" +
                                                            "§b/viscord discord invite§7 - Show Discord invite link\n" +
                                                            "§b/viscord reload§7 - Reload Viscord config (admin)\n" +
                                                            "§b/viscord status§7 - Show Viscord status\n" +
                                                            "§b/viscord fluxer invite§7 - Show Fluxer bot invite link\n" +
                                                            "§7Discord: §b/list§7 - Show online players"),
                                                    false);
                                            return 1;
                                        }))
                        .then(Commands.literal("fluxer")
                                .then(Commands.literal("invite")
                                        .executes(context -> {
                                            String appId = ViscordConfig.CONFIG.fluxerApplicationId.get();
                                            CommandSourceStack source = context.getSource();
                                            
                                            if (appId == null || appId.isEmpty() || appId.equals("YOUR_APPLICATION_ID")) {
                                                source.sendFailure(new TextComponent(
                                                        "§cFluxer Application ID is not configured.\n" +
                                                        "§7Please set 'application_id' in the [fluxer] section of viscord.json"));
                                                return 0;
                                            }
                                            
                                            String inviteUrl = "https://fluxer.app/oauth2/authorize?client_id=" + appId + "&scope=bot";
                                            
                                            MutableComponent clickable = new TextComponent("§aClick here to invite the Fluxer bot to your server!")
                                                    .setStyle(Style.EMPTY
                                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                                                            .withUnderlined(true)
                                                            .withColor(ChatFormatting.GREEN));
                                            source.sendSuccess(clickable, false);
                                            
                                            if (ViscordConfig.CONFIG.debugLogging.get()) {
                                                source.sendSuccess(new TextComponent(
                                                        "§7URL: " + inviteUrl), false);
                                            }
                                            
                                            return 1;
                                        })))));
        // Backward compatibility alias for /vonix commands
        dispatcher.register(
                Commands.literal("vonix")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§e/vonix is deprecated. Use §b/viscord reload§e instead."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("discord")
                                .executes(context -> {
                                    context.getSource().sendSuccess( new TextComponent(
                                            "§e/vonix is deprecated. Use §b/viscord discord§e instead."), false);
                                    return 1;
                                })));

        // /fluxer command alias
        dispatcher.register(
                Commands.literal("fluxer")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> {
                            String appId = ViscordConfig.CONFIG.fluxerApplicationId.get();
                            CommandSourceStack source = context.getSource();
                            
                            if (appId == null || appId.isEmpty() || appId.equals("YOUR_APPLICATION_ID")) {
                                source.sendFailure(new TextComponent(
                                        "§cFluxer Application ID is not configured.\n" +
                                        "§7Please set 'application_id' in the [fluxer] section of viscord.json"));
                                return 0;
                            }
                            
                            String inviteUrl = "https://fluxer.app/oauth2/authorize?client_id=" + appId + "&scope=bot";
                            
                            MutableComponent clickable = new TextComponent("§aClick here to invite the Fluxer bot to your server!")
                                    .setStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                                            .withUnderlined(true)
                                            .withColor(ChatFormatting.GREEN));
                            source.sendSuccess(clickable, false);
                            return 1;
                        })
        );
    }
}
