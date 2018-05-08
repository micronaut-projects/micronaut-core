package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * A default streamed HTTP request.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultStreamedHttpRequest extends DefaultHttpRequest implements StreamedHttpRequest {

    private final Publisher<HttpContent> stream;

    /**
     * @param httpVersion The Http Version
     * @param method      The Http Method
     * @param uri         The URI
     * @param stream      The publisher
     */
    public DefaultStreamedHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, Publisher<HttpContent> stream) {
        super(httpVersion, method, uri);
        this.stream = stream;
    }

    /**
     * @param httpVersion     The Http Version
     * @param method          The Http Method
     * @param uri             The URI
     * @param validateHeaders Whether to validate the headers
     * @param stream          The publisher
     */
    public DefaultStreamedHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean validateHeaders, Publisher<HttpContent> stream) {
        super(httpVersion, method, uri, validateHeaders);
        this.stream = stream;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        stream.subscribe(subscriber);
    }

}
