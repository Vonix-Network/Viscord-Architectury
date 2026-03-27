# Bugfix Requirements Document

## Introduction

When the Fluxer platform is active, in-game events (server online/offline, player join/leave, deaths, advancements) are sent to Discord as plain-text bot messages instead of using the configured event embed format. The `DiscordManager` hardcodes plain-text strings for all Fluxer event calls, bypassing `FluxerPlatform.sendEventEmbed()` entirely. The Discord platform correctly uses rich embeds for the same events.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the server starts and the Fluxer platform is active THEN the system sends a plain-text message like `🟢 **ServerName** is now online!` instead of using the configured server status embed format

1.2 WHEN the server stops and the Fluxer platform is active THEN the system sends a plain-text message like `🔴 **ServerName** is now offline.` instead of using the configured server status embed format

1.3 WHEN a player joins and the Fluxer platform is active THEN the system sends a plain-text message like `➡ **username** joined the game` instead of using the configured player join embed format

1.4 WHEN a player leaves and the Fluxer platform is active THEN the system sends a plain-text message like `⬅ **username** left the game` instead of using the configured player leave embed format

1.5 WHEN a player dies and the Fluxer platform is active THEN the system sends a plain-text message like `☠ <death message>` instead of using the configured death embed format

1.6 WHEN a player earns an advancement and the Fluxer platform is active THEN the system sends a plain-text message like `🏆 **username** has made the advancement **title**` instead of using the configured advancement embed format

### Expected Behavior (Correct)

2.1 WHEN the server starts and the Fluxer platform is active THEN the system SHALL send the event using the same embed data constructed by `EmbedFactory.createServerStatusEmbed()`, formatted appropriately for Fluxer via `FluxerPlatform.sendEventEmbed()`

2.2 WHEN the server stops and the Fluxer platform is active THEN the system SHALL send the event using the same embed data constructed by `EmbedFactory.createServerStatusEmbed()`, formatted appropriately for Fluxer via `FluxerPlatform.sendEventEmbed()`

2.3 WHEN a player joins and the Fluxer platform is active THEN the system SHALL send the event using the same embed data constructed by `EmbedFactory.createPlayerEventEmbed()`, formatted appropriately for Fluxer via `FluxerPlatform.sendEventEmbed()`

2.4 WHEN a player leaves and the Fluxer platform is active THEN the system SHALL send the event using the same embed data constructed by `EmbedFactory.createPlayerEventEmbed()`, formatted appropriately for Fluxer via `FluxerPlatform.sendEventEmbed()`

2.5 WHEN a player dies and the Fluxer platform is active THEN the system SHALL send the event using the death embed data, formatted appropriately for Fluxer via `FluxerPlatform.sendEventEmbed()`

2.6 WHEN a player earns an advancement and the Fluxer platform is active THEN the system SHALL send the event using the same embed data constructed by `EmbedFactory.createAdvancementEmbed()`, formatted appropriately for Fluxer via `FluxerPlatform.sendEventEmbed()`

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the Discord platform is active (not Fluxer) THEN the system SHALL CONTINUE TO send all event embeds as rich Discord embeds via `DiscordPlatform`

3.2 WHEN tridirectional mode is active THEN the system SHALL CONTINUE TO send event embeds to both Discord (as rich embeds) and Fluxer (via `FluxerPlatform.sendEventEmbed()`)

3.3 WHEN a player sends a chat message and the Fluxer platform is active THEN the system SHALL CONTINUE TO relay the message via webhook or bot API as before (chat messages are unaffected)

3.4 WHEN a Discord message is received and relayed to Minecraft THEN the system SHALL CONTINUE TO process and display it in-game as before

3.5 WHEN `FluxerPlatform.sendEventEmbed()` is called with an embed JSON THEN the system SHALL CONTINUE TO format it as bold text (`**title** — description`) since Fluxer does not support native rich embeds
