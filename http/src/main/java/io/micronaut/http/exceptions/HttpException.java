package io.micronaut.http.exceptions;

/**
 * Parent class of all exceptions thrown during HTTP processing
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class HttpException extends RuntimeException {

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
