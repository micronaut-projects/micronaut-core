package io.micronaut.json;

import java.io.IOException;

/**
 * Exception thrown when there is a syntax error in JSON (e.g. mismatched braces).
 */
public final class JsonSyntaxException extends IOException {
    /**
     * Construct a syntax exception from a framework exception (e.g. jackson JsonParseException).
     *
     * @param cause The framework exception
     */
    public JsonSyntaxException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    /**
     * Construct a syntax exception with just a message.
     *
     * @param message The message
     */
    public JsonSyntaxException(String message) {
        super(message);
    }
}
