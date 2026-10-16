package network.vonix.viscord.discord;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridges the DisplayInfo accessor rename between Minecraft 26.2 and 26.3.
 * The 26.x game is unobfuscated, but the accessor shape changed across these
 * releases, so the shared mixin must not compile against only one shape.
 */
public final class AdvancementDisplayCompat {
    private AdvancementDisplayCompat() {
    }

    public static boolean shouldAnnounceChat(DisplayInfo display) {
        Object value = invoke(display, "shouldAnnounceChat", "announceToChat");
        return value instanceof Boolean && (Boolean) value;
    }

    public static Component title(DisplayInfo display) {
        Object value = invoke(display, "getTitle", "title");
        return value instanceof Component ? (Component) value : Component.empty();
    }

    public static Component description(DisplayInfo display) {
        Object value = invoke(display, "getDescription", "description");
        return value instanceof Component ? (Component) value : Component.empty();
    }

    private static Object invoke(DisplayInfo display, String legacyName, String recordName) {
        for (String name : new String[]{legacyName, recordName}) {
            try {
                Method method = DisplayInfo.class.getMethod(name);
                return method.invoke(display);
            } catch (NoSuchMethodException ignored) {
                // Try the accessor name used by the other 26.x release.
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }
}
