/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpParameters;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.DirectByteBodyAccess;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.Certificate;
import java.util.Optional;

/**
 * The mutable copy of a server request that a filter method is given, see
 * {@link HttpRequest#mutate()}, which is still a server request, so
 * that the filter can read the bytes of its body, and so that the route reads them when the filter
 * continues with this request, e.g. after it changed the URI.
 *
 * <p>Like the mutable copy, it shares the headers and the attributes of the request, and it has
 * its own URI, parameters and body object. The bytes of the body are those of the request, and so
 * is the connection: the remote and server addresses, the HTTP version and whether the request is
 * secure.</p>
 *
 * <p>A request that cannot be mutated, e.g. a {@link HttpRequestWrapper} another filter continued
 * with, is given a mutable wrapper, which keeps what the wrapper changed.</p>
 *
 * @param <B> The body type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
sealed class MutableServerRequest<B> extends HttpRequestWrapper<B> implements MutableHttpRequest<B>, ServerHttpRequest<B>, DirectByteBodyAccess
    permits MutableServerRequest.Form {

    private final ServerHttpRequest<B> request;

    private MutableServerRequest(ServerHttpRequest<B> request, MutableHttpRequest<B> mutable) {
        super(mutable);
        this.request = request;
    }

    /**
     * The mutable request to give a filter: the request itself if it is mutable, and its mutable
     * copy otherwise, which is a server request if the request is one.
     *
     * @param request The request
     * @return The mutable request
     */
    static MutableHttpRequest<?> of(HttpRequest<?> request) {
        if (request instanceof MutableHttpRequest<?> mutable) {
            return mutable;
        }
        if (request instanceof FormCapableHttpRequest<?> form) {
            return form(form);
        }
        if (request instanceof ServerHttpRequest<?> server) {
            return server(server);
        }
        return mutable(request);
    }

    /**
     * The mutable copy a filter method was given for a request that is not mutable, as a server
     * request if the request is one.
     *
     * @param request The request
     * @param copy    Its mutable copy
     * @return The copy, as a server request if the request is one
     */
    static MutableHttpRequest<?> of(HttpRequest<?> request, MutableHttpRequest<?> copy) {
        if (request instanceof FormCapableHttpRequest<?> form) {
            return new Form<>(form, cast(copy));
        }
        if (request instanceof ServerHttpRequest<?> server) {
            return new MutableServerRequest<>(server, cast(copy));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static <B> MutableHttpRequest<B> cast(MutableHttpRequest<?> copy) {
        return (MutableHttpRequest<B>) copy;
    }

    private static <B> MutableHttpRequest<B> form(FormCapableHttpRequest<B> request) {
        return new Form<>(request, mutable(request));
    }

    private static <B> MutableHttpRequest<B> server(ServerHttpRequest<B> request) {
        return new MutableServerRequest<>(request, mutable(request));
    }

    /**
     * The mutable copy of a request that is not mutable, like {@link HttpRequest#mutate()}, or a
     * mutable wrapper of it if it has no copy, e.g. an {@link HttpRequestWrapper} a filter
     * continued with.
     *
     * @param request The request, which is not mutable
     * @param <B>     The body type
     * @return Its mutable copy
     */
    static <B> MutableHttpRequest<B> mutable(HttpRequest<B> request) {
        try {
            return request.mutate();
        } catch (UnsupportedOperationException e) {
            return new Overlay<>(request);
        }
    }

    private MutableHttpRequest<B> mutable() {
        return (MutableHttpRequest<B>) getDelegate();
    }

    /**
     * The bytes of the body a request was received with: the bytes of the first request with
     * direct access to them, or of the first server request, through the wrappers around it.
     *
     * @param request The request
     * @return The bytes, or {@code null} if a request replaced them or there are none
     */
    private static @Nullable ByteBody directBytes(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (true) {
            if (current instanceof DirectByteBodyAccess access) {
                return access.byteBodyDirect();
            }
            if (current instanceof ServerHttpRequest<?> server) {
                return server.byteBody();
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
    }

    /**
     * @return The server request this is the mutable copy of
     */
    final ServerHttpRequest<B> request() {
        return request;
    }

    @Override
    public MutableHttpRequest<B> cookie(Cookie cookie) {
        mutable().cookie(cookie);
        return this;
    }

    @Override
    public MutableHttpRequest<B> uri(URI uri) {
        mutable().uri(uri);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MutableHttpRequest<T> body(@Nullable T body) {
        mutable().body(body);
        return (MutableHttpRequest<T>) this;
    }

    @Override
    public @Nullable ByteBody byteBodyDirect() {
        // the bytes of the request are the body unless the body of the copy was set
        return mutable() instanceof DirectByteBodyAccess access ? access.byteBodyDirect() : null;
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return mutable().getHeaders();
    }

    @Override
    public MutableHttpParameters getParameters() {
        return mutable().getParameters();
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        mutable().setConversionService(conversionService);
    }

    @Override
    public MutableHttpRequest<B> mutate() {
        // already mutable, and the changes stay in this request
        return this;
    }

    @Override
    public ByteBody byteBody() {
        return request.byteBody();
    }

    // the connection is the connection of the server request, whatever the URI of the copy

    @Override
    public HttpVersion getHttpVersion() {
        return request.getHttpVersion();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return request.getRemoteAddress();
    }

    @Override
    public InetSocketAddress getServerAddress() {
        return request.getServerAddress();
    }

    @Override
    public @Nullable String getServerName() {
        return request.getServerName();
    }

    @Override
    public boolean isSecure() {
        return request.isSecure();
    }

    @Override
    public Optional<SSLSession> getSslSession() {
        return request.getSslSession();
    }

    @Override
    public Optional<Certificate> getCertificate() {
        return request.getCertificate();
    }

    @Override
    public ByteBodyFactory byteBodyFactory() {
        return request.byteBodyFactory();
    }

    @Override
    public String toString() {
        return getMethodName() + " " + getUri();
    }

    /**
     * The mutable copy of a server request with a form body, which reads the form of the request.
     *
     * @param <B> The body type
     */
    static final class Form<B> extends MutableServerRequest<B> implements FormCapableHttpRequest<B> {

        private Form(FormCapableHttpRequest<B> request, MutableHttpRequest<B> mutable) {
            super(request, mutable);
        }

        private FormCapableHttpRequest<B> form() {
            return (FormCapableHttpRequest<B>) request();
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return form().getRawFormFields();
        }

        @Override
        public boolean hasFormBody() {
            return form().hasFormBody();
        }

        @Override
        public void addDisposalResource(Runnable dispose) {
            form().addDisposalResource(dispose);
        }
    }

    /**
     * A mutable wrapper of a request without a mutable copy: the headers of the request, and its
     * own URI and body object.
     *
     * @param <B> The body type
     */
    private static final class Overlay<B> extends MutableHttpRequestWrapper<B> implements DirectByteBodyAccess {

        private boolean bodySet;
        /**
         * Whether the body was set to {@code null}: the request has no body then, whatever the
         * body of the request it wraps.
         */
        private boolean cleared;

        private Overlay(HttpRequest<B> request) {
            super(ConversionService.SHARED, request);
        }

        @Override
        public <T> MutableHttpRequest<T> body(@Nullable T body) {
            bodySet = true;
            cleared = body == null;
            return super.body(body);
        }

        @Override
        public @Nullable ByteBody byteBodyDirect() {
            // the bytes of the wrapped request while the body was not set, not even to null
            return bodySet ? null : directBytes(getDelegate());
        }

        @Override
        public Optional<B> getBody() {
            // none if it was cleared
            return cleared ? Optional.empty() : super.getBody();
        }

        @Override
        public <T> Optional<T> getBody(Class<T> type) {
            return cleared ? Optional.empty() : super.getBody(type);
        }

        @Override
        public <T> Optional<T> getBody(ArgumentConversionContext<T> conversionContext) {
            return cleared ? Optional.empty() : super.getBody(conversionContext);
        }

        @Override
        public String getPath() {
            URI uri = getUri();
            // the path of a URI that was changed, and otherwise the path of the request
            return uri == getDelegate().getUri() ? super.getPath() : uri.getRawPath();
        }
    }
}
