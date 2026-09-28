/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.cookie.Cookies;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * A wrapper around a {@link HttpRequest}.
 *
 * @param <B> The Http body type
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpRequestWrapper<B> extends HttpMessageWrapper<B> implements HttpRequest<B> {

    /**
     * Where the body of each wrapper class comes from.
     */
    private static final ClassValue<BodySource> BODY_SOURCES = new ClassValue<>() {
        @Override
        protected BodySource computeValue(Class<?> type) {
            Class<?> declaring;
            try {
                declaring = type.getMethod("getBody").getDeclaringClass();
            } catch (NoSuchMethodException | LinkageError e) {
                // not looked up, e.g. a native image without the metadata of the class, which
                // fails with a MissingReflectionRegistrationError: the bodies are compared
                return BodySource.OVERRIDDEN;
            }
            if (declaring == HttpMessageWrapper.class) {
                return BodySource.DELEGATE;
            }
            if (declaring == MutableHttpRequestWrapper.class) {
                return BodySource.MUTABLE;
            }
            return BodySource.OVERRIDDEN;
        }
    };

    /**
     * @param delegate The Http Request
     */
    public HttpRequestWrapper(HttpRequest<B> delegate) {
        super(delegate);
    }

    @Override
    public HttpRequest<B> getDelegate() {
        return (HttpRequest<B>) super.getDelegate();
    }

    @Override
    public HttpVersion getHttpVersion() {
        return getDelegate().getHttpVersion();
    }

    @Override
    public Collection<MediaType> accept() {
        return getDelegate().accept();
    }

    @Override
    public Optional<Principal> getUserPrincipal() {
        return getDelegate().getUserPrincipal();
    }

    @Override
    public <T extends Principal> Optional<T> getUserPrincipal(Class<T> principalType) {
        return getDelegate().getUserPrincipal(principalType);
    }

    @Override
    public HttpRequest<B> setAttribute(CharSequence name, @Nullable Object value) {
        return getDelegate().setAttribute(name, value);
    }

    @Override
    public Optional<Locale> getLocale() {
        return getDelegate().getLocale();
    }

    @Override
    public Optional<Certificate> getCertificate() {
        return getDelegate().getCertificate();
    }

    @Override
    public Optional<SSLSession> getSslSession() {
        return getDelegate().getSslSession();
    }

    @Override
    public Cookies getCookies() {
        return getDelegate().getCookies();
    }

    @Override
    public HttpParameters getParameters() {
        return getDelegate().getParameters();
    }

    @Override
    public HttpMethod getMethod() {
        return getDelegate().getMethod();
    }

    @Override
    public String getMethodName() {
        return getDelegate().getMethodName();
    }

    @Override
    public URI getUri() {
        return getDelegate().getUri();
    }

    @Override
    public String getPath() {
        return getDelegate().getPath();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return getDelegate().getRemoteAddress();
    }

    @Override
    public InetSocketAddress getServerAddress() {
        return getDelegate().getServerAddress();
    }

    @Override
    public @Nullable String getServerName() {
        return getDelegate().getServerName();
    }

    @Override
    public boolean isSecure() {
        return getDelegate().isSecure();
    }

    /**
     * Whether the given request is a wrapper whose body is not the body of the request it wraps,
     * e.g. a wrapper a filter continued with that returns a replacement from {@code getBody()}:
     * the bytes of the requests it wraps are then not its body.
     *
     * <p>A wrapper that cannot have replaced the body is never asked for it: a wrapper whose class
     * does not override {@code getBody()}, like a plain {@link HttpRequestWrapper} or a subclass
     * that only overrides e.g. {@code getHeaders()}, a {@link MutableHttpRequestWrapper} whose body
     * was not set, and a {@link BodyPreservingRequestWrapper}. Asking a wrapper for its body
     * decodes the body of the request it wraps, and that decoding may consume the bytes of the
     * request, e.g. the input stream of a servlet request, or produce a new object on each call.
     * Any other wrapper replaced the body if its body is not, by identity, the body of the request
     * it wraps.</p>
     *
     * <p>Whether a class overrides {@code getBody()} is looked up once per class. Where it cannot
     * be looked up, e.g. in a native image without the reflection metadata that
     * micronaut-http registers for the subclasses of {@link HttpMessageWrapper}, the bodies are
     * compared.</p>
     *
     * @param request The request
     * @return Whether it is a wrapper that replaced the body of the request it wraps
     * @since 5.3.0
     */
    @Internal
    @SuppressWarnings("ReferenceEquality") // by identity
    public static boolean replacesBody(HttpRequest<?> request) {
        if (!(request instanceof HttpRequestWrapper<?> wrapper) || request instanceof BodyPreservingRequestWrapper) {
            return false;
        }
        Class<?> type = wrapper.getClass();
        BodySource source;
        if (type == HttpRequestWrapper.class) {
            source = BodySource.DELEGATE;
        } else if (type == MutableHttpRequestWrapper.class) {
            source = BodySource.MUTABLE;
        } else {
            source = BODY_SOURCES.get(type);
        }
        return switch (source) {
            case DELEGATE -> false;
            case MUTABLE -> ((MutableHttpRequestWrapper<?>) wrapper).isBodySet();
            // by identity: a replacement that only compares equal, e.g. a sanitized copy, is still a replacement
            case OVERRIDDEN -> wrapper.getBody().orElse(null) != wrapper.getDelegate().getBody().orElse(null);
        };
    }

    /**
     * The layers of a request: the request itself, then the request each
     * {@link HttpRequestWrapper} wraps, down to the innermost request, which is not a wrapper.
     *
     * @param request The request
     * @return The request and the requests it wraps, outermost first
     * @since 5.3.0
     */
    @Internal
    public static Iterable<HttpRequest<?>> unwrap(HttpRequest<?> request) {
        return () -> new Iterator<>() {
            private @Nullable HttpRequest<?> next = request;

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public HttpRequest<?> next() {
                HttpRequest<?> current = next;
                if (current == null) {
                    throw new NoSuchElementException();
                }
                next = current instanceof HttpRequestWrapper<?> wrapper ? wrapper.getDelegate() : null;
                return current;
            }
        };
    }

    /**
     * Where the body of a wrapper class comes from, by the class that declares its
     * {@code getBody()}.
     */
    private enum BodySource {
        /**
         * Declared by {@link HttpMessageWrapper}: the body of the delegate.
         */
        DELEGATE,
        /**
         * Declared by {@link MutableHttpRequestWrapper}: the body that was set, or else the body
         * of the delegate.
         */
        MUTABLE,
        /**
         * Declared by a subclass, or not known: it may return anything.
         */
        OVERRIDDEN
    }
}
