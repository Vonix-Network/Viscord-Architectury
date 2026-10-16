package network.vonix.viscord.discord;

import org.javacord.api.DiscordApi;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class BotClientMessageCacheTest {

    @Test
    void disablesHistoricalMessageRetentionBeforeEventHandling() {
        DiscordApi api = mock(DiscordApi.class);

        BotClient.configureMessageCache(api);

        var ordered = inOrder(api);
        ordered.verify(api).setMessageCacheSize(0, 0);
        ordered.verify(api).setAutomaticMessageCacheCleanupEnabled(true);
        ordered.verifyNoMoreInteractions();
    }
}
