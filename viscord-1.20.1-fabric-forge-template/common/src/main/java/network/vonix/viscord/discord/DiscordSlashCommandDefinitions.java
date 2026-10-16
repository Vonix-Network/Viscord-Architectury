package network.vonix.viscord.discord;

import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.EnumSet;
import java.util.List;

/**
 * Native Discord application-command contract owned by Viscord.
 *
 * <p>The definitions are deliberately small and explicit. Registration is
 * server-scoped and updates only commands owned by this Discord application;
 * it never bulk-overwrites unrelated applications' commands.</p>
 */
public final class DiscordSlashCommandDefinitions {
    public static final String LINK = "link";
    public static final String LIST = "list";
    public static final String HOME = "home";
    public static final String HELP = "help";
    public static final String STATUS = "status";

    private DiscordSlashCommandDefinitions() {
    }

    public record Definition(
            String name,
            String description,
            List<SlashCommandOption> options,
            boolean administratorOnly) {
    }

    public static List<Definition> all() {
        return List.of(
                new Definition(
                        LINK,
                        "Link your Discord account to your Minecraft account",
                        List.of(new SlashCommandOptionBuilder()
                                .setType(SlashCommandOptionType.STRING)
                                .setName("code")
                                .setDescription("The six-digit code shown by /viscord discord link")
                                .setRequired(true)
                                .setMinLength(6)
                                .setMaxLength(6)
                                .build()),
                        false),
                new Definition(
                        LIST,
                        "Show the players currently online",
                        List.of(),
                        false),
                new Definition(
                        HOME,
                        "Safely send your linked Minecraft player to a named home",
                        List.of(new SlashCommandOptionBuilder()
                                .setType(SlashCommandOptionType.STRING)
                                .setName("name")
                                .setDescription("Home name, such as home")
                                .setRequired(true)
                                .setMinLength(1)
                                .setMaxLength(32)
                                .build()),
                        false),
                new Definition(
                        HELP,
                        "Show Viscord Discord commands",
                        List.of(),
                        false),
                new Definition(
                        STATUS,
                        "Show Viscord and Minecraft server status",
                        List.of(),
                        true)
        );
    }

    public static SlashCommandBuilder builder(Definition definition) {
        SlashCommandBuilder builder = new SlashCommandBuilder()
                .setName(definition.name())
                .setDescription(definition.description())
                .setOptions(definition.options())
                .setEnabledInDms(false);

        if (definition.administratorOnly()) {
            builder.setDefaultEnabledForPermissions(EnumSet.of(PermissionType.ADMINISTRATOR));
        } else {
            builder.setDefaultEnabledForEveryone();
        }
        return builder;
    }
}
