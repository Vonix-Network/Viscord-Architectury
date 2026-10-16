package network.vonix.viscord.discord;

/**
 * Exception thrown when advancement data extraction from Discord embeds fails.
 * This exception is used to handle various error conditions during the extraction
 * process, such as missing fields, malformed data, or validation failures.
 *
 * Requirements: 6.4
 */
public class ExtractionException extends Exception {

    /**
     * Creates a new ExtractionException with the specified message.
     *
     * @param message The detail message explaining the extraction failure
     */
    public ExtractionException(String message) {
        super(message);
    }

    /**
     * Creates a new ExtractionException with the specified message and cause.
     *
     * @param message The detail message explaining the extraction failure
     * @param cause The underlying cause of the extraction failure
     */
    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
