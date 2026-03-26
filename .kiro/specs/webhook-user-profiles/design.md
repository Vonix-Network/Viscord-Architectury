# Design Document: Webhook User Profiles

## Overview

This is a surgical wiring fix inside `DiscordManager`. The tridirectional relay already receives full sender identity (display name + avatar URL) from both Discord and Fluxer, and both webhook clients already accept those fields. The only gap is that `bridgeDiscordToFluxer` and `bridgeFluxerToDiscord` currently discard the sender's identity before calling the webhook clients.

The fix:
- `bridgeDiscordToFluxer`: extract `authorName` and `avatarUrl` from the `MessageCreateEvent` and pass them to `fluxerWebhookClient.sendMessage`.
- `bridgeFluxerToDiscord`: the `onFluxerMessage` callback already receives `username` and `avatarUrl`; thread them through to `webhookClient.sendMessage` instead of passing `""`.

No new classes, no signature changes, no new config keys.

## Architecture

The existing data flow already carries the required data end-to-end. The fix closes the two gaps marked `[GAP]`:

```
Discord MessageCreateEvent
  └─ author.getDisplayName()   ──────────────────────────────────────────────────────────┐
  └─ author.getAvatarUrl()     ──────────────────────────────────────────────────────────┤
       │                                                                                  │
       ▼                                                                                  ▼
  DiscordManager.onDiscordMessage()                                         [GAP CLOSED]
       └─ bridgeDiscordToFluxer(authorName, content, message)  ──► fluxerWebhookClient.sendMessage(username, avatarUrl, content)
                                                                         └─ POST /slack  (icon_url set when non-empty)

Fluxer Gateway / FluxerReceiver
  └─ onMessage(username, message, avatarUrl)  ────────────────────────────────────────────┐
       │                                                                                   │
       ▼                                                                                   ▼
  DiscordManager.onFluxerMessage()                                          [GAP CLOSED]
       └─ bridgeFluxerToDiscord(username, message, avatarUrl)  ──► webhookClient.sendMessage(username, avatarUrl, content)
                                                                         └─ POST Discord webhook  (avatar_url set when non-empty)
```

## Components and Interfaces

### DiscordManager (modified)

`bridgeDiscordToFluxer(String authorName, String content, Message message)`

Currently uses `fluxerBotClient.sendMessage` (bot API, no identity). The fix switches to `fluxerWebhookClient.sendMessage` when a Fluxer webhook URL is configured, passing the real `authorName` and `avatarUrl` extracted from the `MessageCreateEvent`.

Avatar URL extraction from Javacord `MessageAuthor`:
```java
String avatarUrl = message.getAuthor().getAvatar().getUrl().toString();
```
Null-safe: wrap in a helper that returns `""` if the author has no avatar.

`bridgeFluxerToDiscord(String username, String message, String avatarUrl)`

Signature gains the `avatarUrl` parameter (currently the method only takes `username` and `message`). The call site in `onFluxerMessage` already has `avatarUrl` available — it just needs to be threaded through. The `webhookClient.sendMessage` call changes from `("...", "", ...)` to `(username, avatarUrl != null ? avatarUrl : "", ...)`.

### WebhookClient (unchanged)

`sendMessage(String username, String avatarUrl, String content)` — already includes `avatar_url` in the JSON payload when non-empty. No changes needed.

### FluxerWebhookClient (unchanged)

`sendMessage(String username, String avatarUrl, String content)` — already conditionally includes `icon_url` when `avatarUrl` is non-empty. No changes needed.

### FluxerBotClient.MessageHandler (unchanged)

`onMessage(String username, String message, String avatarUrl)` — already delivers avatarUrl. No changes needed.

### FluxerReceiver.MessageHandler (unchanged)

`onFluxerMessage(String username, String message, String avatarUrl)` — already delivers avatarUrl. No changes needed.

## Data Models

No new data models. The relevant fields are plain `String` values already present in existing method signatures and event objects.

| Field | Source (Discord→Fluxer) | Source (Fluxer→Discord) |
|---|---|---|
| `username` | `event.getMessage().getAuthor().getDisplayName()` | `username` param from `onFluxerMessage` callback |
| `avatarUrl` | `event.getMessage().getAuthor().getAvatar().getUrl().toString()` (null-safe) | `avatarUrl` param from `onFluxerMessage` callback |
| `content` | cleaned message content (already computed) | cleaned message content (already computed) |

Null normalization rule: any `null` avatarUrl is coerced to `""` before being passed to either webhook client. Both clients already handle `""` correctly (omit the avatar field from the payload).

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Discord sender identity is forwarded to Fluxer webhook

*For any* Discord message relayed to Fluxer with tridirectional enabled, the `username` and `avatarUrl` passed to `FluxerWebhookClient.sendMessage` must equal the author's display name and avatar URL from the originating `MessageCreateEvent` (with null avatar normalized to `""`).

**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Fluxer sender identity is forwarded to Discord webhook

*For any* Fluxer message relayed to Discord with tridirectional enabled, the `username` and `avatarUrl` passed to `WebhookClient.sendMessage` must equal the values delivered by the `onFluxerMessage` callback (with null avatar normalized to `""`).

**Validates: Requirements 2.1, 2.2, 2.3**

### Property 3: FluxerWebhookClient icon_url presence matches avatarUrl

*For any* call to `FluxerWebhookClient.sendMessage`, the outgoing JSON payload must contain `icon_url` if and only if `avatarUrl` is non-empty; and must always contain `username` and `text` regardless of `avatarUrl`.

**Validates: Requirements 1.4, 3.3**

### Property 4: WebhookClient avatar_url presence matches avatarUrl

*For any* call to `WebhookClient.sendMessage`, the outgoing JSON payload must contain `avatar_url` when `avatarUrl` is non-empty, and must always contain `username` and `content` regardless of `avatarUrl`.

**Validates: Requirements 2.4, 3.2**

### Property 5: Tridirectional-off guard

*For any* incoming message when tridirectional mode is disabled, neither `WebhookClient.sendMessage` nor `FluxerWebhookClient.sendMessage` is invoked by the relay bridge methods.

**Validates: Requirements 4.5**

## Error Handling

| Condition | Behavior |
|---|---|
| `avatarUrl` is `null` | Coerce to `""` before passing to webhook client |
| Discord webhook URL not configured | `isDiscordConfigured()` returns false; `bridgeFluxerToDiscord` returns early, no exception |
| Fluxer webhook URL not configured | `fluxerWebhookClient` has null `webhookId`/`webhookToken`; `sendMessage` returns early, no exception |
| Javacord avatar URL throws | Wrap extraction in try/catch; fall back to `""` |
| Tridirectional disabled | Guard conditions at top of each bridge method prevent any relay logic from running |

## Testing Strategy

### Unit Tests

Focus on the two modified bridge methods and the null-normalization helper:

- `bridgeDiscordToFluxer` with a mock `MessageCreateEvent` — assert `FluxerWebhookClient.sendMessage` is called with the correct username and avatarUrl.
- `bridgeDiscordToFluxer` with a null avatar — assert `avatarUrl` argument is `""`.
- `bridgeFluxerToDiscord` with a non-null avatarUrl — assert `WebhookClient.sendMessage` receives it.
- `bridgeFluxerToDiscord` with `avatarUrl = null` — assert `""` is passed.
- Both bridge methods with tridirectional disabled — assert no webhook call is made.

### Property-Based Tests

Use a property-based testing library (e.g., `junit-quickcheck` or `jqwik` for Java).

Each property test runs a minimum of 100 iterations with randomly generated inputs.

**Property 1 test** — `Feature: webhook-user-profiles, Property 1: Discord sender identity forwarded to Fluxer`
Generate random `(displayName, avatarUrl, messageContent)` triples. Invoke the relay logic with a mock event carrying those values. Assert `FluxerWebhookClient.sendMessage` was called with `(displayName, avatarUrl ?? "", messageContent)`.

**Property 2 test** — `Feature: webhook-user-profiles, Property 2: Fluxer sender identity forwarded to Discord`
Generate random `(username, avatarUrl, messageContent)` triples including null avatarUrl. Invoke `bridgeFluxerToDiscord`. Assert `WebhookClient.sendMessage` was called with `(username, avatarUrl ?? "", messageContent)`.

**Property 3 test** — `Feature: webhook-user-profiles, Property 3: FluxerWebhookClient icon_url presence`
Generate random `(username, avatarUrl, content)` where avatarUrl is either a valid URL string or `""`. Capture the JSON built by `FluxerWebhookClient.sendMessage`. Assert `icon_url` is present iff `avatarUrl` is non-empty, and `username`/`text` are always present.

**Property 4 test** — `Feature: webhook-user-profiles, Property 4: WebhookClient avatar_url presence`
Same shape as Property 3 but for `WebhookClient`. Assert `avatar_url` is present iff `avatarUrl` is non-empty, and `username`/`content` are always present.

**Property 5 test** — `Feature: webhook-user-profiles, Property 5: Tridirectional-off guard`
Generate random messages. With tridirectional config disabled, invoke both bridge methods. Assert zero calls to either webhook client's `sendMessage`.
