package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Delegate for Streamed HTTP Request.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
final class DelegateStreamedHttpRequest extends DelegateHttpRequest implements StreamedHttpRequest {

    private final Publisher<HttpContent> stream;

    /**
     * @param request The Http request
     * @param stream  The publisher
     */
    DelegateStreamedHttpRequest(HttpRequest request, Publisher<HttpContent> stream) {
        super(request);
        this.stream = stream;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        stream.subscribe(subscriber);
    }
}
