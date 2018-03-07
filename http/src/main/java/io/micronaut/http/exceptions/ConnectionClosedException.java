package io.micronaut.http.exceptions;

/**
 * Exception thrown when the client or server unexpectedly closes the connection
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConnectionClosedException extends HttpException {
    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
