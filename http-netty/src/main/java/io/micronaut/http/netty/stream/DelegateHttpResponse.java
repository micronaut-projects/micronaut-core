package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Delegate for HTTP Response.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DelegateHttpResponse extends DelegateHttpMessage implements HttpResponse {

    protected final HttpResponse response;

    /**
     * @param response The Http response
     */
    DelegateHttpResponse(HttpResponse response) {
        super(response);
        this.response = response;
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        response.setStatus(status);
        return this;
    }

    @Override
    @Deprecated
    public HttpResponseStatus getStatus() {
        return response.status();
    }

    @Override
    public HttpResponseStatus status() {
        return response.status();
    }

    @Override
    public HttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }
}
