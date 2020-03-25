/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.http.cookie.Cookies;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Locale;
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

    @NonNull
    @Override
    public Optional<Principal> getUserPrincipal() {
        return getDelegate().getUserPrincipal();
    }

    @NonNull
    @Override
    public <T extends Principal> Optional<T> getUserPrincipal(Class<T> principalType) {
        return getDelegate().getUserPrincipal(principalType);
    }

    @Override
    public HttpRequest<B> setAttribute(CharSequence name, Object value) {
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
    public String getServerName() {
        return getDelegate().getServerName();
    }

    @Override
    public boolean isSecure() {
        return getDelegate().isSecure();
    }
}
