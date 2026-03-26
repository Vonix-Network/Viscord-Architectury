# Requirements Document

## Introduction

In the tridirectional chat relay (Discord <> Fluxer <> Minecraft), when a message from one platform is relayed to another via webhook, the outgoing webhook currently does not pass through the original sender's identity. Instead, messages appear as if sent by the bot itself. This feature ensures that when a Discord user's message is relayed to Fluxer, and when a Fluxer user's message is relayed to Discord, the outgoing webhook call uses the original sender's avatar URL and display name — so messages appear in the destination platform as if sent by that user, not the bot.

The fix is localized to `DiscordManager`: the `bridgeDiscordToFluxer` and `bridgeFluxerToDiscord` methods already receive sender profile data (or can access it from the event), but currently pass empty/placeholder values to the webhook clients. Both `WebhookClient.sendMessage(username, avatarUrl, content)` and `FluxerWebhookClient.sendMessage(username, avatarUrl, content)` already accept these parameters — they just need to be populated correctly.

Scope: chat messages only in the tridirectional relay path. Join/leave/death/advancement events and Minecraft player messages are out of scope.

## Glossary

- **DiscordManager**: The central coordinator class that handles all platform bridging logic, including `bridgeDiscordToFluxer` and `bridgeFluxerToDiscord`.
- **WebhookClient**: The class that sends HTTP POST requests to Discord webhook URLs. Its `sendMessage(username, avatarUrl, content)` method accepts sender identity fields.
- **FluxerWebhookClient**: The class that sends HTTP POST requests to Fluxer webhook URLs (Slack-compatible format). Its `sendMessage(username, avatarUrl, content)` method accepts sender identity fields.
- **BotClient**: The Discord bot client that receives incoming Discord messages via Javacord. The `MessageCreateEvent` it provides contains the author's display name and avatar URL.
- **FluxerBotClient**: The Fluxer WebSocket gateway client. Its `MessageHandler.onMessage(username, message, avatarUrl)` callback already delivers the sender's username and avatar URL.
- **FluxerReceiver**: The HTTP server that receives incoming Fluxer webhook payloads. Its `MessageHandler.onFluxerMessage(username, message, avatarUrl)` callback already delivers the sender's username and avatar URL.
- **Sender_Profile**: The combination of a sender's display name and avatar URL as received from the originating platform.
- **Relay_Path**: The code path in `DiscordManager` that takes a message received from one platform and forwards it to another platform's webhook.
- **Tridirectional Chat**: The mode in which chat messages are bridged between Discord and Fluxer (and Minecraft) simultaneously.

## Requirements

### Requirement 1: Relay Discord Sender Profile to Fluxer Webhook

**User Story:** As a Fluxer user, I want messages relayed from Discord to appear with the original Discord sender's avatar and display name, so that I can see who in Discord sent the message without it appearing as the bot.

#### Acceptance Criteria

1. WHEN a Discord user sends a chat message and tridirectional relay to Fluxer is enabled, THE DiscordManager SHALL extract the sender's display name from the `MessageCreateEvent` author and pass it as the `username` parameter to `FluxerWebhookClient.sendMessage`.
2. WHEN a Discord user sends a chat message and tridirectional relay to Fluxer is enabled, THE DiscordManager SHALL extract the sender's avatar URL from the `MessageCreateEvent` author and pass it as the `avatarUrl` parameter to `FluxerWebhookClient.sendMessage`.
3. IF the Discord sender's avatar URL is null or empty, THEN THE DiscordManager SHALL pass an empty string as the `avatarUrl` parameter to `FluxerWebhookClient.sendMessage`, and THE FluxerWebhookClient SHALL omit the `icon_url` field from the outgoing payload.
4. WHEN the Discord sender's avatar URL is a valid non-empty URL, THE FluxerWebhookClient SHALL include it as the `icon_url` field in the Slack-format webhook payload.

### Requirement 2: Relay Fluxer Sender Profile to Discord Webhook

**User Story:** As a Discord user, I want messages relayed from Fluxer to appear with the original Fluxer sender's avatar and display name, so that I can see who in Fluxer sent the message without it appearing as the bot.

#### Acceptance Criteria

1. WHEN a Fluxer user sends a chat message (received via `FluxerBotClient` or `FluxerReceiver`) and tridirectional relay to Discord is enabled, THE DiscordManager SHALL use the `username` value already provided by the message handler callback as the `username` parameter to `WebhookClient.sendMessage`.
2. WHEN a Fluxer user sends a chat message and tridirectional relay to Discord is enabled, THE DiscordManager SHALL use the `avatarUrl` value already provided by the message handler callback as the `avatarUrl` parameter to `WebhookClient.sendMessage`.
3. IF the Fluxer sender's avatar URL is null, THEN THE DiscordManager SHALL pass an empty string as the `avatarUrl` parameter to `WebhookClient.sendMessage`.
4. WHEN the Fluxer sender's avatar URL is a valid non-empty URL, THE WebhookClient SHALL include it as the `avatar_url` field in the Discord webhook payload.

### Requirement 3: Graceful Fallback When Avatar URL Is Absent

**User Story:** As a server administrator, I want the relay to continue working even when a sender has no avatar set, so that messages are never dropped due to a missing avatar URL.

#### Acceptance Criteria

1. IF the avatar URL received from either platform is null, THEN THE DiscordManager SHALL treat it as an empty string before passing it to the webhook client.
2. WHEN `WebhookClient.sendMessage` is called with an empty `avatarUrl`, THE WebhookClient SHALL still send the webhook message with the `username` and `content` fields populated.
3. WHEN `FluxerWebhookClient.sendMessage` is called with an empty `avatarUrl`, THE FluxerWebhookClient SHALL still send the webhook message with the `username` and `text` fields populated, and SHALL omit the `icon_url` field.
4. IF the Discord webhook URL is not configured, THEN THE DiscordManager SHALL skip the Discord relay without throwing an exception.
5. IF the Fluxer webhook URL is not configured, THEN THE DiscordManager SHALL skip the Fluxer webhook relay without throwing an exception.

### Requirement 4: Non-Breaking — Existing Method Signatures Unchanged

**User Story:** As a developer, I want the profile relay fix to be a targeted change in `DiscordManager` only, so that no existing callers of `WebhookClient` or `FluxerWebhookClient` are broken.

#### Acceptance Criteria

1. THE `WebhookClient.sendMessage(String username, String avatarUrl, String content)` method signature SHALL remain unchanged.
2. THE `FluxerWebhookClient.sendMessage(String username, String avatarUrl, String content)` method signature SHALL remain unchanged.
3. THE `FluxerBotClient.MessageHandler.onMessage(String username, String message, String avatarUrl)` interface SHALL remain unchanged.
4. THE `FluxerReceiver.MessageHandler.onFluxerMessage(String username, String message, String avatarUrl)` interface SHALL remain unchanged.
5. WHILE tridirectional mode is disabled, THE DiscordManager SHALL NOT invoke the profile relay logic, preserving existing non-tridirectional behavior.
