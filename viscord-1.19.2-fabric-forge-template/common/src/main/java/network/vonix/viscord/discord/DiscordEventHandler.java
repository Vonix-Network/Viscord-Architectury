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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.config.ViscordConfig;

/**
 * Minecraft event handler for Discord integration.
 * Handles commands, player join/leave, death, and advancement events.
 */
public class DiscordEventHandler {

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
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
                if (DiscordManager.getInstance().isRunning() && ViscordConfig.CONFIG.sendDeath.get()) {
                    // 1.19.2 API: getLocalizedDeathMessage takes player as parameter
                    String deathMessage = source.getLocalizedDeathMessage(player).getString();
                    DiscordManager.getInstance().sendServerStatusMessage("Player Died", "💀 " + deathMessage, 0x000000);
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
                            String invite = ViscordConfig.CONFIG.inviteUrl.get();
                            CommandSourceStack source = context.getSource();

                            if (invite == null || invite.isEmpty()) {
                                source.sendSuccess(Component.literal(
                                        "§cDiscord invite URL is not configured."), false);
                            } else {
                                MutableComponent clickable = Component
                                        .literal("Click Here to join the Discord!")
                                        .withStyle(style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite))
                                                .withUnderlined(true)
                                                .withColor(ChatFormatting.AQUA));
                                source.sendSuccess(clickable, false);
                            }
                            return 1;
                        })
                        .then(Commands.literal("messages")
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerMessagesFiltered(player.getUUID(), false);
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§aCross-server messages enabled!"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerMessagesFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§cCross-server messages disabled!"), false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasServerMessagesFiltered(player.getUUID());
                                    context.getSource().sendSuccess(Component.literal(
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
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§aEvent messages enabled!"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setEventsFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§cEvent messages disabled!"), false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasEventsFiltered(player.getUUID());
                                    context.getSource().sendSuccess(Component.literal(
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
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§aServer system messages enabled!"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            DiscordManager.getInstance()
                                                    .setServerSystemMessagesFiltered(player.getUUID(), true);
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§cServer system messages disabled!"), false);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean isFiltered = DiscordManager.getInstance()
                                            .hasServerSystemMessagesFiltered(player.getUUID());
                                    context.getSource().sendSuccess(Component.literal(
                                            "§7Server system messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                            false);
                                    return 1;
                                })));

        // /vonix discord commands
        dispatcher.register(
                Commands.literal("vonix")
                        .then(Commands.literal("discord")
                                .then(Commands.literal("link")
                                        .executes(context -> {
                                            if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                                                context.getSource().sendFailure(
                                                        Component.literal("§cAccount linking is disabled."));
                                                return 0;
                                            }

                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String code = DiscordManager.getInstance().generateLinkCode(player);

                                            if (code != null) {
                                                int expiryMinutes = ViscordConfig.CONFIG.linkCodeExpiry.get() / 60;
                                                context.getSource().sendSuccess(Component.literal(
                                                        "§aYour link code is: §e" + code + "\n" +
                                                                "§7Use §b/link " + code
                                                                + "§7 in Discord to link your account.\n" +
                                                                "§7Code expires in " + expiryMinutes + " minutes."),
                                                        false);
                                                return 1;
                                            } else {
                                                context.getSource().sendFailure(
                                                        Component.literal("§cFailed to generate link code."));
                                                return 0;
                                            }
                                        }))
                                .then(Commands.literal("unlink")
                                        .executes(context -> {
                                            if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                                                context.getSource().sendFailure(
                                                        Component.literal("§cAccount linking is disabled."));
                                                return 0;
                                            }

                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean success = DiscordManager.getInstance()
                                                    .unlinkAccount(player.getUUID());

                                            if (success) {
                                                context.getSource().sendSuccess(Component.literal(
                                                        "§aYour Discord account has been unlinked."), false);
                                                return 1;
                                            } else {
                                                context.getSource().sendFailure(
                                                        Component
                                                                .literal("§cYou don't have a linked Discord account."));
                                                return 0;
                                            }
                                        }))
                                .then(Commands.literal("messages")
                                        .then(Commands.literal("enable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setServerMessagesFiltered(player.getUUID(), false);
                                                    context.getSource().sendSuccess(Component.literal(
                                                            "§aServer messages enabled!"), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setServerMessagesFiltered(player.getUUID(), true);
                                                    context.getSource().sendSuccess(Component.literal(
                                                            "§cServer messages disabled!"), false);
                                                    return 1;
                                                }))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean isFiltered = DiscordManager.getInstance()
                                                    .hasServerMessagesFiltered(player.getUUID());
                                            context.getSource().sendSuccess(Component.literal(
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
                                                    context.getSource().sendSuccess(Component.literal(
                                                            "§aEvent messages enabled!"), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DiscordManager.getInstance()
                                                            .setEventsFiltered(player.getUUID(), true);
                                                    context.getSource().sendSuccess(Component.literal(
                                                            "§cEvent messages disabled!"), false);
                                                    return 1;
                                                }))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean isFiltered = DiscordManager.getInstance()
                                                    .hasEventsFiltered(player.getUUID());
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§7Event messages: " + (isFiltered ? "§cDisabled" : "§aEnabled")),
                                                    false);
                                            return 1;
                                        }))
                                .then(Commands.literal("help")
                                        .executes(context -> {
                                            context.getSource().sendSuccess(Component.literal(
                                                    "§6§l=== Viscord Discord Commands ===\n" +
                                                            "§b/discord§7 - Show Discord invite link\n" +
                                                            "§b/vonix discord link§7 - Generate account link code\n" +
                                                            "§b/vonix discord unlink§7 - Unlink your Discord\n" +
                                                            "§b/vonix discord messages§7 - Toggle server messages\n" +
                                                            "§b/vonix discord events§7 - Toggle event messages\n" +
                                                            "§7Discord: §b/list§7 - Show online players"),
                                                    false);
                                            return 1;
                                        }))));
    }
}
