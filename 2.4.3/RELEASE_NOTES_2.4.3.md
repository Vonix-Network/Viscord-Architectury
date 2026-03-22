# Viscord 2.4.3

## 🐛 Bug Fixes
* **Duplicate Startup Embeds:** Fixed an issue where the Discord bot would duplicate the startup embed when status updates were enabled in Fluxer mode.
* **Tridirectional Event Embeds:** Fixed Tridirectional Chat routing where event embeds (e.g., Advancements, Death, Server Startup, Player Joins) were only being sent to Fluxer and not being bridged back to the Discord channel. They are now correctly sent to both platforms when Tridirectional Chat is enabled.