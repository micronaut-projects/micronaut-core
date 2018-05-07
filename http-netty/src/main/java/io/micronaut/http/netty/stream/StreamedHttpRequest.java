package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpRequest;

/**
 * Combines {@link HttpRequest} and {@link StreamedHttpMessage} into one
 * message. So it represents an http request with a stream of
 * {@link io.netty.handler.codec.http.HttpContent} messages that can be subscribed to.
 */
public interface StreamedHttpRequest extends HttpRequest, StreamedHttpMessage {
}
