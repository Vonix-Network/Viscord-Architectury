package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.entity.server.Server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration system for managing server prefixes in Discord advancement messages.
 * Provides configurable server prefix mapping, unique prefix generation for different servers,
 * and configuration validation with fallback handling.
 * 
 * Requirements: 4.4, 4.5
 */
public class ServerPrefixConfig {
    
    // Default prefix used when no specific configuration is available
    private static final String DEFAULT_PREFIX = "MC";
    
    // Maximum length for server prefixes to ensure readability
    private static final int MAX_PREFIX_LENGTH = 16;
    
    // Thread-safe storage for server ID to prefix mappings
    private final Map<Long, String> serverPrefixMap = new ConcurrentHashMap<>();
    
    // Thread-safe storage for tracking used prefixes to ensure uniqueness
    private final Set<String> usedPrefixes = ConcurrentHashMap.newKeySet();
    
    // Fallback prefix configuration for unknown servers
    private String fallbackPrefix = DEFAULT_PREFIX;
    
    // Counter for generating unique numeric suffixes
    private int prefixCounter = 1;
    
    /**
     * Gets the configured prefix for a specific Discord server.
     * If no prefix is configured for the server, generates a unique prefix.
     * 
     * @param serverId The Discord server ID
     * @return The server prefix, never null or empty
     */
    public String getServerPrefix(long serverId) {
        return serverPrefixMap.computeIfAbsent(serverId, this::generateUniquePrefix);
    }
    
    /**
     * Gets the configured prefix for a specific Discord server.
     * Convenience method that accepts a Javacord Server object.
     * 
     * @param server The Discord server object
     * @return The server prefix, never null or empty
     */
    public String getServerPrefix(Server server) {
        if (server == null) {
            return getFallbackPrefix();
        }
        return getServerPrefix(server.getId());
    }
    
    /**
     * Sets a custom prefix for a specific Discord server.
     * Validates the prefix and ensures uniqueness across all servers.
     * 
     * @param serverId The Discord server ID
     * @param prefix The desired prefix
     * @throws IllegalArgumentException if the prefix is invalid or already in use
     */
    public void setServerPrefix(long serverId, String prefix) {
        if (!isValidPrefix(prefix)) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix + 
                ". Prefix must be 1-" + MAX_PREFIX_LENGTH + " characters, alphanumeric or common symbols only.");
        }
        
        // Check if prefix is already in use by another server
        String normalizedPrefix = normalizePrefix(prefix);
        if (usedPrefixes.contains(normalizedPrefix)) {
            // Check if it's used by the same server (allowed) or different server (not allowed)
            String currentPrefix = serverPrefixMap.get(serverId);
            if (currentPrefix == null || !normalizedPrefix.equals(normalizePrefix(currentPrefix))) {
                throw new IllegalArgumentException("Prefix '" + prefix + "' is already in use by another server");
            }
        }
        
        // Remove old prefix from used set if it exists
        String oldPrefix = serverPrefixMap.get(serverId);
        if (oldPrefix != null) {
            usedPrefixes.remove(normalizePrefix(oldPrefix));
        }
        
        // Set new prefix
        serverPrefixMap.put(serverId, prefix);
        usedPrefixes.add(normalizedPrefix);
        
        Viscord.LOGGER.info("[Discord] Set server prefix for server {} to '{}'", serverId, prefix);
    }
    
    /**
     * Removes the custom prefix configuration for a server.
     * The server will use the generated unique prefix on next access.
     * 
     * @param serverId The Discord server ID
     */
    public void removeServerPrefix(long serverId) {
        String removedPrefix = serverPrefixMap.remove(serverId);
        if (removedPrefix != null) {
            usedPrefixes.remove(normalizePrefix(removedPrefix));
            Viscord.LOGGER.info("[Discord] Removed server prefix for server {}", serverId);
        }
    }
    
    /**
     * Gets the fallback prefix used for unknown or invalid servers.
     * 
     * @return The fallback prefix
     */
    public String getFallbackPrefix() {
        return fallbackPrefix;
    }
    
    /**
     * Sets the fallback prefix used for unknown or invalid servers.
     * 
     * @param fallbackPrefix The new fallback prefix
     * @throws IllegalArgumentException if the prefix is invalid
     */
    public void setFallbackPrefix(String fallbackPrefix) {
        if (!isValidPrefix(fallbackPrefix)) {
            throw new IllegalArgumentException("Invalid fallback prefix: " + fallbackPrefix);
        }
        this.fallbackPrefix = fallbackPrefix;
        Viscord.LOGGER.info("[Discord] Set fallback prefix to '{}'", fallbackPrefix);
    }
    
    /**
     * Gets all configured server prefixes.
     * 
     * @return A copy of the server prefix mappings
     */
    public Map<Long, String> getAllServerPrefixes() {
        return new HashMap<>(serverPrefixMap);
    }
    
    /**
     * Clears all server prefix configurations.
     * Servers will use generated unique prefixes on next access.
     */
    public void clearAllPrefixes() {
        serverPrefixMap.clear();
        usedPrefixes.clear();
        prefixCounter = 1;
        Viscord.LOGGER.info("[Discord] Cleared all server prefix configurations");
    }
    
    /**
     * Validates that a prefix meets the requirements.
     * 
     * @param prefix The prefix to validate
     * @return true if the prefix is valid, false otherwise
     */
    private boolean isValidPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = prefix.trim();
        if (trimmed.length() > MAX_PREFIX_LENGTH) {
            return false;
        }
        
        // Allow alphanumeric characters and common symbols
        return trimmed.matches("[a-zA-Z0-9\\-_\\[\\]\\(\\)\\{\\}]+");
    }
    
    /**
     * Normalizes a prefix for uniqueness checking.
     * Converts to lowercase and trims whitespace.
     * 
     * @param prefix The prefix to normalize
     * @return The normalized prefix
     */
    private String normalizePrefix(String prefix) {
        return prefix.trim().toLowerCase();
    }
    
    /**
     * Generates a unique prefix for a server ID.
     * Uses the server ID to create a deterministic but unique prefix.
     * 
     * @param serverId The Discord server ID
     * @return A unique prefix for the server
     */
    private String generateUniquePrefix(long serverId) {
        // Try to create a prefix based on server ID
        String basePrefix = "S" + Math.abs(serverId % 1000);
        
        // Ensure uniqueness by adding counter if needed
        String candidatePrefix = basePrefix;
        while (usedPrefixes.contains(normalizePrefix(candidatePrefix))) {
            candidatePrefix = basePrefix + "_" + prefixCounter++;
            
            // Prevent infinite loops by limiting attempts
            if (prefixCounter > 9999) {
                candidatePrefix = "MC" + System.currentTimeMillis() % 1000;
                break;
            }
        }
        
        // Ensure the generated prefix doesn't exceed max length
        if (candidatePrefix.length() > MAX_PREFIX_LENGTH) {
            candidatePrefix = candidatePrefix.substring(0, MAX_PREFIX_LENGTH);
        }
        
        usedPrefixes.add(normalizePrefix(candidatePrefix));
        Viscord.LOGGER.info("[Discord] Generated unique prefix '{}' for server {}", candidatePrefix, serverId);
        
        return candidatePrefix;
    }
    
    /**
     * Validates the current configuration and logs any issues.
     * This method can be called periodically to ensure configuration integrity.
     * 
     * @return true if the configuration is valid, false if issues were found
     */
    public boolean validateConfiguration() {
        boolean isValid = true;
        Set<String> duplicateCheck = new HashSet<>();
        
        for (Map.Entry<Long, String> entry : serverPrefixMap.entrySet()) {
            String prefix = entry.getValue();
            String normalized = normalizePrefix(prefix);
            
            // Check for invalid prefixes
            if (!isValidPrefix(prefix)) {
                Viscord.LOGGER.warn("[Discord] Invalid prefix '{}' found for server {}", prefix, entry.getKey());
                isValid = false;
            }
            
            // Check for duplicates
            if (duplicateCheck.contains(normalized)) {
                Viscord.LOGGER.warn("[Discord] Duplicate prefix '{}' found for server {}", prefix, entry.getKey());
                isValid = false;
            } else {
                duplicateCheck.add(normalized);
            }
        }
        
        // Validate fallback prefix
        if (!isValidPrefix(fallbackPrefix)) {
            Viscord.LOGGER.warn("[Discord] Invalid fallback prefix '{}'", fallbackPrefix);
            isValid = false;
        }
        
        if (isValid) {
            Viscord.LOGGER.debug("[Discord] Server prefix configuration validation passed");
        } else {
            Viscord.LOGGER.warn("[Discord] Server prefix configuration validation failed");
        }
        
        return isValid;
    }
}
