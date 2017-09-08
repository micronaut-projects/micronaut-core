package org.particleframework.http.exceptions;

/**
 * Exception thrown when the content length exceeds the allowed amount
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ContentLengthExceededException extends HttpException {
    public ContentLengthExceededException(String message) {
        super(message);
    }

    public ContentLengthExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContentLengthExceededException(long advertisedLength, long receivedLength) {
        this("The received length ["+receivedLength+"] exceeds the specified content length ["+advertisedLength+"]");
    }
}
