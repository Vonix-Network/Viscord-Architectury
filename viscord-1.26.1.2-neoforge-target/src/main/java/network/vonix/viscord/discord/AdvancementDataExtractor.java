package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedField;

/**
 * Extractor class for parsing advancement data from Discord embeds.
 * Extracts player name, advancement title, and description from embed fields
 * with validation for required fields and non-empty values.
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
 */
public class AdvancementDataExtractor {

    private final AdvancementEmbedDetector detector;

    /**
     * Creates a new AdvancementDataExtractor with the provided detector.
     */
    public AdvancementDataExtractor() {
        this.detector = new AdvancementEmbedDetector();
    }

    /**
     * Extracts advancement data from a Discord embed.
     * Enhanced with comprehensive error handling and detailed error messages.
     *
     * @param embed The Discord embed to extract data from
     * @return AdvancementData containing the extracted information
     * @throws ExtractionException if the embed is invalid or missing required fields
     */
    public AdvancementData extractFromEmbed(Embed embed) throws ExtractionException {
        if (embed == null) {
            throw new ExtractionException("Embed cannot be null");
        }

        try {
            // Validate that this is actually an advancement embed
            if (!detector.isAdvancementEmbed(embed)) {
                throw new ExtractionException("Embed is not an advancement embed");
            }

            // Extract advancement type with error handling
            AdvancementType type;
            try {
                type = detector.getAdvancementType(embed);
            } catch (Exception typeError) {
                // Log the error but continue with default type
                Viscord.LOGGER.warn("[Discord] Error determining advancement type, using NORMAL as fallback. " +
                        "Error: {} | Embed title: {}",
                        typeError.getMessage(), embed.getTitle().orElse("none"));
                type = AdvancementType.NORMAL;
            }

            // Extract data from embed fields with enhanced error handling
            String playerName = null;
            String advancementTitle = null;
            String advancementDescription = null;

            // Parse embed fields to extract advancement data
            try {
                for (EmbedField field : embed.getFields()) {
                    try {
                        String fieldName = field.getName();
                        String fieldValue = field.getValue();

                        if (fieldName == null || fieldValue == null) {
                            continue;
                        }

                        // Normalize field names for comparison (case-insensitive)
                        String normalizedName = fieldName.toLowerCase().trim();
                        String normalizedValue = fieldValue.trim();

                        // Skip empty values
                        if (normalizedValue.isEmpty()) {
                            continue;
                        }

                        // Extract player name from various possible field names
                        if (normalizedName.contains("player") || normalizedName.contains("user") || normalizedName.contains("name")) {
                            if (playerName == null) { // Take the first match
                                playerName = normalizedValue;
                            }
                        }
                        // Extract advancement title
                        else if (normalizedName.contains("advancement") || normalizedName.contains("title") || normalizedName.contains("achievement")) {
                            if (advancementTitle == null) { // Take the first match
                                advancementTitle = normalizedValue;
                            }
                        }
                        // Extract advancement description
                        else if (normalizedName.contains("description") || normalizedName.contains("desc") || normalizedName.contains("details")) {
                            if (advancementDescription == null) { // Take the first match
                                advancementDescription = normalizedValue;
                            }
                        }
                    } catch (Exception fieldError) {
                        // Log field processing error but continue with other fields
                        Viscord.LOGGER.warn("[Discord] Error processing embed field, skipping. " +
                                "Field name: {} | Error: {}",
                                field.getName(), fieldError.getMessage());
                        continue;
                    }
                }
            } catch (Exception fieldsError) {
                throw new ExtractionException("Error processing embed fields: " + fieldsError.getMessage(), fieldsError);
            }

            // Also check embed title and description as fallbacks with error handling
            if (advancementTitle == null) {
                try {
                    if (embed.getTitle().isPresent()) {
                        String embedTitle = embed.getTitle().get().trim();
                        if (!embedTitle.isEmpty() && !isGenericTitle(embedTitle)) {
                            advancementTitle = embedTitle;
                        }
                    }
                } catch (Exception titleError) {
                    Viscord.LOGGER.warn("[Discord] Error accessing embed title for fallback extraction: {}",
                        titleError.getMessage());
                }
            }

            if (advancementDescription == null) {
                try {
                    if (embed.getDescription().isPresent()) {
                        String embedDesc = embed.getDescription().get().trim();
                        if (!embedDesc.isEmpty()) {
                            advancementDescription = embedDesc;
                        }
                    }
                } catch (Exception descError) {
                    Viscord.LOGGER.warn("[Discord] Error accessing embed description for fallback extraction: {}",
                        descError.getMessage());
                }
            }

            // Validate that all required fields were found with detailed error messages
            validateExtractedData(playerName, advancementTitle, advancementDescription, embed);

            // Create and return the AdvancementData object with error handling
            try {
                return new AdvancementData(playerName, advancementTitle, advancementDescription, type);
            } catch (Exception dataCreationError) {
                throw new ExtractionException("Failed to create AdvancementData object: " + dataCreationError.getMessage(),
                        dataCreationError);
            }

        } catch (ExtractionException e) {
            // Re-throw ExtractionExceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap any other exceptions in ExtractionException with context
            throw new ExtractionException("Unexpected error during advancement data extraction: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that all required data was successfully extracted.
     * Enhanced with detailed error messages and context information.
     *
     * @param playerName The extracted player name
     * @param advancementTitle The extracted advancement title
     * @param advancementDescription The extracted advancement description
     * @param embed The original embed for context in error messages
     * @throws ExtractionException if any required field is missing or empty
     */
    private void validateExtractedData(String playerName, String advancementTitle, String advancementDescription, Embed embed)
            throws ExtractionException {

        // Check each field individually to provide specific error messages that match test expectations
        if (playerName == null || playerName.trim().isEmpty()) {
            // Add context information for debugging but keep the main message for test compatibility
            String contextInfo = "";
            try {
                contextInfo = " Embed context: Title='" + embed.getTitle().orElse("none") +
                             "', Fields=" + embed.getFields().size() +
                             ", Footer='" + embed.getFooter().map(f -> f.getText().orElse("none")).orElse("none") + "'";
            } catch (Exception contextError) {
                contextInfo = " (Error getting embed context: " + contextError.getMessage() + ")";
            }

            // Log the detailed context for debugging while keeping the simple message for tests
            Viscord.LOGGER.debug("[Discord] Player name validation failed.{}", contextInfo);
            throw new ExtractionException("Player name not found in embed fields");
        }

        if (advancementTitle == null || advancementTitle.trim().isEmpty()) {
            String contextInfo = "";
            try {
                contextInfo = " Embed context: Title='" + embed.getTitle().orElse("none") +
                             "', Fields=" + embed.getFields().size() +
                             ", Footer='" + embed.getFooter().map(f -> f.getText().orElse("none")).orElse("none") + "'";
            } catch (Exception contextError) {
                contextInfo = " (Error getting embed context: " + contextError.getMessage() + ")";
            }

            Viscord.LOGGER.debug("[Discord] Advancement title validation failed.{}", contextInfo);
            throw new ExtractionException("Advancement title not found in embed fields");
        }

        if (advancementDescription == null || advancementDescription.trim().isEmpty()) {
            String contextInfo = "";
            try {
                contextInfo = " Embed context: Title='" + embed.getTitle().orElse("none") +
                             "', Fields=" + embed.getFields().size() +
                             ", Footer='" + embed.getFooter().map(f -> f.getText().orElse("none")).orElse("none") + "'";
            } catch (Exception contextError) {
                contextInfo = " (Error getting embed context: " + contextError.getMessage() + ")";
            }

            Viscord.LOGGER.debug("[Discord] Advancement description validation failed.{}", contextInfo);
            throw new ExtractionException("Advancement description not found in embed fields");
        }
    }

    /**
     * Checks if a title is a generic advancement type title that shouldn't be used
     * as the actual advancement title.
     *
     * @param title The title to check
     * @return true if the title is generic, false otherwise
     */
    private boolean isGenericTitle(String title) {
        String lowerTitle = title.toLowerCase();
        return lowerTitle.equals("advancement made") ||
               lowerTitle.equals("goal reached") ||
               lowerTitle.equals("challenge complete") ||
               lowerTitle.equals("advancement") ||
               lowerTitle.equals("goal") ||
               lowerTitle.equals("challenge");
    }
}
