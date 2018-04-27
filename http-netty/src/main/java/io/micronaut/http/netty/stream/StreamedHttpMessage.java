package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import org.reactivestreams.Publisher;

/**
 * Combines {@link HttpMessage} and {@link Publisher} into one
 * message. So it represents an http message with a stream of {@link HttpContent}
 * messages that can be subscribed to.
 *
 * Note that receivers of this message <em>must</em> consume the publisher,
 * since the publisher will exert back pressure up the stream if not consumed.
 */
public interface StreamedHttpMessage extends HttpMessage, Publisher<HttpContent> {
}
