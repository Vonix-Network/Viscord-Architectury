# Implementation Tasks: Webhook User Profiles

## Task List

- [x] 1. Fix `bridgeFluxerToDiscord` in all MC version templates
  - [x] 1.1 Update `bridgeFluxerToDiscord` signature to accept `avatarUrl` parameter and pass it to `webhookClient.sendMessage` in `viscord-1.18.2-fabric-forge-template`
  - [x] 1.2 Update call site in `onFluxerMessage` to thread `avatarUrl` through to `bridgeFluxerToDiscord` in `viscord-1.18.2-fabric-forge-template`
  - [x] 1.3 Apply the same `bridgeFluxerToDiscord` fix to `viscord-1.19.2-fabric-forge-template`
  - [x] 1.4 Apply the same `bridgeFluxerToDiscord` fix to `viscord-1.20.1-fabric-forge-template`
  - [x] 1.5 Apply the same `bridgeFluxerToDiscord` fix to `viscord-1.21.1-fabric-neoforge-template`

- [x] 2. Fix `bridgeDiscordToFluxer` in all MC version templates
  - [x] 2.1 Update `bridgeDiscordToFluxer` in `viscord-1.18.2-fabric-forge-template` to extract `avatarUrl` from `MessageCreateEvent` author and switch from `fluxerBotClient.sendMessage` to `fluxerWebhookClient.sendMessage` (when webhook URL is configured), passing real `authorName` and `avatarUrl`
  - [x] 2.2 Apply the same `bridgeDiscordToFluxer` fix to `viscord-1.19.2-fabric-forge-template`
  - [x] 2.3 Apply the same `bridgeDiscordToFluxer` fix to `viscord-1.20.1-fabric-forge-template`
  - [x] 2.4 Apply the same `bridgeDiscordToFluxer` fix to `viscord-1.21.1-fabric-neoforge-template`

- [x] 3. Null-safety: normalize null avatarUrl to empty string
  - [x] 3.1 In both bridge methods across all templates, ensure `avatarUrl` is coerced to `""` when null before being passed to webhook clients

- [x] 4. Write tests
  - [x] 4.1 Write unit tests for `bridgeFluxerToDiscord`: verify `WebhookClient.sendMessage` receives the correct `username` and `avatarUrl` (including null→`""` normalization)
  - [x] 4.2 Write unit tests for `bridgeDiscordToFluxer`: verify `FluxerWebhookClient.sendMessage` receives the correct `username` and `avatarUrl` from the mock `MessageCreateEvent`
  - [x] 4.3 Write unit test: tridirectional disabled → neither webhook client's `sendMessage` is called
  - [x] 4.4 Write property-based test for Property 1 (Discord sender identity forwarded to Fluxer) using jqwik — generate random `(displayName, avatarUrl, content)` and assert forwarding
  - [x] 4.5 Write property-based test for Property 2 (Fluxer sender identity forwarded to Discord) using jqwik — generate random `(username, avatarUrl, content)` including null avatarUrl
  - [x] 4.6 Write property-based test for Property 3 (FluxerWebhookClient `icon_url` presence matches avatarUrl non-emptiness)
  - [x] 4.7 Write property-based test for Property 4 (WebhookClient `avatar_url` presence matches avatarUrl non-emptiness)
