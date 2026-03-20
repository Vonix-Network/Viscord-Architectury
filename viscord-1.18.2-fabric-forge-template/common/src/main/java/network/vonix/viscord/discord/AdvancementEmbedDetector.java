package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.entity.message.embed.Embed;
import java.util.Set;

/**
 * Detector class for identifying advancement embeds from Discord messages.
 * Uses footer-based detection with advancement keywords to distinguish
 * advancement embeds from regular chat embeds.
 * 
 * Requirements: 1.1, 1.2
 */
public class AdvancementEmbedDetector {
    
    /**
     * Keywords used to identify advancement embeds in footer text.
     * These keywords are checked case-insensitively in embed footers.
     */
    private static final Set<String> ADVANCEMENT_FOOTER_KEYWORDS = Set.of(
        "advancement", "goal", "challenge", "task"
    );
    
    /**
     * Determines if a Discord embed is an advancement embed by checking
     * the footer text for advancement-related keywords.
     * Enhanced with comprehensive error handling and detailed logging.
     * 
     * @param embed The Discord embed to analyze
     * @return true if the embed is identified as an advancement embed, false otherwise
     */
    public boolean isAdvancementEmbed(Embed embed) {
        if (embed == null) {
            return false;
        }
        
        try {
            return hasAdvancementFooter(embed);
        } catch (Exception e) {
            // Log the error but don't throw - return false to be safe
            // This ensures that detection errors don't crash the entire message processing
            Viscord.LOGGER.warn("[Discord] Error during advancement embed detection, treating as non-advancement embed. " +
                    "Error: {} | Embed title: {} | Fields: {}", 
                    e.getMessage(), embed.getTitle().orElse("none"), embed.getFields().size());
            return false;
        }
    }
    
    /**
     * Determines the advancement type from embed content by analyzing
     * the title and footer text for type-specific keywords.
     * Enhanced with comprehensive error handling and fallback behavior.
     * 
     * @param embed The Discord embed to analyze
     * @return The corresponding AdvancementType, defaults to NORMAL if not recognized
     */
    public AdvancementType getAdvancementType(Embed embed) {
        if (embed == null) {
            return AdvancementType.NORMAL;
        }
        
        try {
            // First check the title for advancement type indicators
            try {
                if (embed.getTitle().isPresent()) {
                    String title = embed.getTitle().get().toLowerCase();
                    
                    if (title.contains("challenge")) {
                        return AdvancementType.CHALLENGE;
                    } else if (title.contains("goal")) {
                        return AdvancementType.GOAL;
                    }
                }
            } catch (Exception titleError) {
                Viscord.LOGGER.debug("[Discord] Error checking embed title for advancement type: {}", 
                        titleError.getMessage());
                // Continue to check footer
            }
            
            // Check footer text for additional type indicators
            try {
                if (embed.getFooter().isPresent()) {
                    String footerText = embed.getFooter().get().getText()
                            .map(String::toLowerCase)
                            .orElse("");
                    
                    if (footerText.contains("challenge")) {
                        return AdvancementType.CHALLENGE;
                    } else if (footerText.contains("goal")) {
                        return AdvancementType.GOAL;
                    }
                }
            } catch (Exception footerError) {
                Viscord.LOGGER.debug("[Discord] Error checking embed footer for advancement type: {}", 
                        footerError.getMessage());
                // Fall through to default
            }
            
            // Default to NORMAL if no specific type indicators found
            return AdvancementType.NORMAL;
            
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error determining advancement type, using NORMAL as fallback. " +
                    "Error: {} | Embed title: {}", 
                    e.getMessage(), embed.getTitle().orElse("none"));
            return AdvancementType.NORMAL;
        }
    }
    
    /**
     * Checks if the embed footer contains advancement-related keywords.
     * This is the primary method for identifying advancement embeds.
     * Enhanced with comprehensive error handling and detailed logging.
     * 
     * @param embed The Discord embed to check
     * @return true if the footer contains advancement keywords, false otherwise
     */
    private boolean hasAdvancementFooter(Embed embed) {
        try {
            if (!embed.getFooter().isPresent()) {
                return false;
            }
            
            try {
                String footerText = embed.getFooter().get().getText()
                        .map(String::toLowerCase)
                        .orElse("");
                
                // Check if any of the advancement keywords are present in the footer
                boolean hasKeyword = ADVANCEMENT_FOOTER_KEYWORDS.stream()
                        .anyMatch(keyword -> footerText.contains(keyword));
                
                if (Viscord.LOGGER.isDebugEnabled() && hasKeyword) {
                    Viscord.LOGGER.debug("[Discord] Advancement embed detected by footer keywords. " +
                            "Footer: '{}' | Matched keywords: {}", 
                            footerText, 
                            ADVANCEMENT_FOOTER_KEYWORDS.stream()
                                .filter(keyword -> footerText.contains(keyword))
                                .toArray());
                }
                
                return hasKeyword;
                
            } catch (Exception footerTextError) {
                Viscord.LOGGER.warn("[Discord] Error accessing footer text for advancement detection: {}", 
                        footerTextError.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error checking embed footer for advancement keywords: {}", 
                    e.getMessage());
            return false;
        }
    }
}
