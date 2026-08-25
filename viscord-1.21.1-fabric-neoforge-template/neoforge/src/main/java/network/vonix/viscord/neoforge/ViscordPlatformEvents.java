package network.vonix.viscord.neoforge;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import network.vonix.viscord.platform.PlatformEvents;
import java.nio.file.Path;

public final class ViscordPlatformEvents implements PlatformEvents {
    @Override public void register(Callbacks c) {
        CommandRegistrationEvent.EVENT.register((d, r, s) -> c.commands().accept(d));
        LifecycleEvent.SERVER_STARTED.register(server -> c.serverStarted().accept(server));
        LifecycleEvent.SERVER_STOPPING.register(server -> c.serverStopping().accept(server));
        PlayerEvent.PLAYER_JOIN.register(player -> c.playerJoin().accept(player));
        PlayerEvent.PLAYER_QUIT.register(player -> c.playerQuit().accept(player));
        EntityEvent.LIVING_DEATH.register((e, s) -> { c.livingDeath().accept(e, s); return EventResult.pass(); });
    }
    @Override public Path configDirectory() { return dev.architectury.platform.Platform.getConfigFolder(); }
}
