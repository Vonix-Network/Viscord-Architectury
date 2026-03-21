# Viscord 2.4.2

## 🐛 Bug Fixes
* **Fluxer Bot Initialization:** Fixed an issue where the Discord bot would not turn on when using the Fluxer platform alongside Tridirectional Chat.
* **Fluxer Bot Status:** Fixed a bug where the Discord bot would not connect to show the server online status ("Playing X/Y") when using the Fluxer platform without Tridirectional Chat. The bot will now connect solely for status updates if a token is provided and status updates are enabled.
* **Fluxer Server Embeds:** Fixed server startup, join, and leave embeds not being sent when using the Fluxer platform.
* **Fluxer Channel ID Exceptions:** Resolved a bug that caused an exception when formatting Fluxer webhook events by attempting to parse a Discord channel ID.
* **Fluxer Event Webhooks:** Fixed an issue where event embeds (Achievements, Deaths, etc.) were not being sent to Fluxer when using the Fluxer platform configuration. Events are now correctly bridged to the configured Fluxer webhook.
* **Webhook Reliability:** Improved the resilience of the webhook client by catching errors to prevent silent thread crashes for misconfigured URLs.
* **Account Linking Messages:** Improved error messages for account linking in Minecraft when the bot is disabled or not running.