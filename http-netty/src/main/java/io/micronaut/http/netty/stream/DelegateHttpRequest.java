package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

class DelegateHttpRequest extends DelegateHttpMessage implements HttpRequest {

    protected final HttpRequest request;

    public DelegateHttpRequest(HttpRequest request) {
        super(request);
        this.request = request;
    }

    @Override
    public HttpRequest setMethod(HttpMethod method) {
        request.setMethod(method);
        return this;
    }

    @Override
    public HttpRequest setUri(String uri) {
        request.setUri(uri);
        return this;
    }

    @Override
    @Deprecated
    public HttpMethod getMethod() {
        return request.method();
    }

    @Override
    public HttpMethod method() {
        return request.method();
    }

    @Override
    @Deprecated
    public String getUri() {
        return request.uri();
    }

    @Override
    public String uri() {
        return request.uri();
    }

    @Override
    public HttpRequest setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }
}
