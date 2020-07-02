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
package io.micronaut.http.netty.cookies;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/**
 * A wrapper around a Netty cookie.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyCookie implements Cookie {

    private final io.netty.handler.codec.http.cookie.Cookie nettyCookie;

    /**
     * @param nettyCookie The Netty cookie
     */
    public NettyCookie(io.netty.handler.codec.http.cookie.Cookie nettyCookie) {
        this.nettyCookie = nettyCookie;
    }

    /**
     * @param name  The name
     * @param value The value
     */
    public NettyCookie(String name, String value) {
        Objects.requireNonNull(name, "Argument name cannot be null");
        Objects.requireNonNull(value, "Argument value cannot be null");

        this.nettyCookie = new DefaultCookie(name, value);
    }

    /**
     * @return The Netty cookie
     */
    public io.netty.handler.codec.http.cookie.Cookie getNettyCookie() {
        return nettyCookie;
    }

    @Override
    public @NonNull String getName() {
        return nettyCookie.name();
    }

    @Override
    public @NonNull String getValue() {
        return nettyCookie.value();
    }

    @Override
    public String getDomain() {
        return nettyCookie.domain();
    }

    @Override
    public String getPath() {
        return nettyCookie.path();
    }

    @Override
    public boolean isHttpOnly() {
        return nettyCookie.isHttpOnly();
    }

    @Override
    public boolean isSecure() {
        return nettyCookie.isSecure();
    }

    @Override
    public long getMaxAge() {
        return nettyCookie.maxAge();
    }

    @Override
    public @NonNull Cookie maxAge(long maxAge) {
        nettyCookie.setMaxAge(maxAge);
        return this;
    }

    @Override
    public Optional<SameSite> getSameSite() {
        if (nettyCookie instanceof io.netty.handler.codec.http.cookie.DefaultCookie) {
            io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite sameSite = ((io.netty.handler.codec.http.cookie.DefaultCookie) nettyCookie).sameSite();
            if (sameSite != null) {
                return Optional.of(SameSite.valueOf(sameSite.name()));
            }
        }
        return Optional.empty();
    }

    @Override
    public @NonNull Cookie sameSite(@Nullable SameSite sameSite) {
        if (nettyCookie instanceof io.netty.handler.codec.http.cookie.DefaultCookie) {
            ((io.netty.handler.codec.http.cookie.DefaultCookie) nettyCookie).setSameSite(sameSite == null ? null : io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite.valueOf(sameSite.name()));
        }
        return this;
    }

    @Override
    public @NonNull Cookie value(@NonNull String value) {
        nettyCookie.setValue(value);
        return this;
    }

    @Override
    public @NonNull Cookie domain(String domain) {
        nettyCookie.setDomain(domain);
        return this;
    }

    @Override
    public @NonNull Cookie path(String path) {
        nettyCookie.setPath(path);
        return this;
    }

    @Override
    public @NonNull Cookie secure(boolean secure) {
        nettyCookie.setSecure(secure);
        return this;
    }

    @Override
    public @NonNull Cookie httpOnly(boolean httpOnly) {
        nettyCookie.setHttpOnly(httpOnly);
        return this;
    }

    @Override
    public int compareTo(Cookie o) {
        NettyCookie nettyCookie = (NettyCookie) o;
        return nettyCookie.nettyCookie.compareTo(this.nettyCookie);
    }
}
