package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * A default streamed HTTP response.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultStreamedHttpResponse extends DefaultHttpResponse implements StreamedHttpResponse {

    private final Publisher<HttpContent> stream;

    /**
     * @param version The Http Version
     * @param status  The Http response status
     * @param stream  The publisher
     */
    public DefaultStreamedHttpResponse(HttpVersion version, HttpResponseStatus status, Publisher<HttpContent> stream) {
        super(version, status);
        this.stream = stream;
    }

    /**
     * @param version         The Http Version
     * @param status          The Http response status
     * @param validateHeaders Whether to validate the headers
     * @param stream          The publisher
     */
    public DefaultStreamedHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders, Publisher<HttpContent> stream) {
        super(version, status, validateHeaders);
        this.stream = stream;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        stream.subscribe(subscriber);
    }

}
