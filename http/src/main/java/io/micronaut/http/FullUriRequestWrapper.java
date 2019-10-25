package io.micronaut.http;

import java.net.URI;

public class FullUriRequestWrapper<B> extends HttpRequestWrapper<B> {

    private final URI requestUri;

    /**
     * @param delegate The Http Request
     */
    public FullUriRequestWrapper(HttpRequest<B> delegate, URI requestUri) {
        super(delegate);
        this.requestUri = requestUri;
    }

    @Override
    public URI getUri() {
        return requestUri;
    }
}
