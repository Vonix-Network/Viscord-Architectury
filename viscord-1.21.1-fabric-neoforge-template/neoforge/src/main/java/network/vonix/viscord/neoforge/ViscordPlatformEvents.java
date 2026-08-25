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
        LifecycleEvent.SERVER_STARTED.register(c.serverStarted());
        LifecycleEvent.SERVER_STOPPING.register(c.serverStopping());
        PlayerEvent.PLAYER_JOIN.register(c.playerJoin());
        PlayerEvent.PLAYER_QUIT.register(c.playerQuit());
        EntityEvent.LIVING_DEATH.register((e, s) -> { c.livingDeath().accept(e, s); return EventResult.pass(); });
    }
    @Override public Path configDirectory() { return dev.architectury.platform.Platform.getConfigFolder(); }
}
