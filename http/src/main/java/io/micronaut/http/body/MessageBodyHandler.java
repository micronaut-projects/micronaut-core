package io.micronaut.http.body;

/**
 * A handler combines a reader and a writer.
 *
 * @param <T> The tye
 * @see MessageBodyReader
 * @see MessageBodyWriter
 * @since 4.0.0
 */
public interface MessageBodyHandler<T> extends MessageBodyReader<T>, MessageBodyWriter<T> {
}
