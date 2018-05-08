package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Delegate for Streamed HTTP Response.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
final class DelegateStreamedHttpResponse extends DelegateHttpResponse implements StreamedHttpResponse {

    private final Publisher<HttpContent> stream;

    /**
     * @param response The {@link HttpResponse}
     * @param stream   The {@link Publisher} for {@link HttpContent}
     */
    DelegateStreamedHttpResponse(HttpResponse response, Publisher<HttpContent> stream) {
        super(response);
        this.stream = stream;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        stream.subscribe(subscriber);
    }
}
