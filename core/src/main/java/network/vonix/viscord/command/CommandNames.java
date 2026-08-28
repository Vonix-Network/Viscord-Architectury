package network.vonix.viscord.command;

import java.util.List;

/**
 * Brigadier root literals and stable subcommands shared by Viscord cells.
 *
 * <p>Registration and permission checks stay in version/loader code.
 */
public final class CommandNames {
    public static final String DISCORD = "discord";
    public static final String VISCORD = "viscord";
    public static final String VONIX_DEPRECATED_ALIAS = "vonix";

    public static final String INVITE = "invite";
    public static final String MESSAGES = "messages";
    public static final String EVENTS = "events";
    public static final String SERVER_MESSAGES = "servermessages";
    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";
    public static final String RELOAD = "reload";
    public static final String STATUS = "status";
    public static final String LINK = "link";
    public static final String UNLINK = "unlink";
    public static final String HELP = "help";

    private CommandNames() {}

    public static List<String> rootLiterals() {
        return List.of(DISCORD, VISCORD, VONIX_DEPRECATED_ALIAS);
    }
}
