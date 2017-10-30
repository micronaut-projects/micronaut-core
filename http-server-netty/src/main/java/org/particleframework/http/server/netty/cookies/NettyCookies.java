/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.cookies;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.cookie.Cookies;

import java.util.*;

/**
 * Delegates to {@link Cookie}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyCookies implements Cookies {

    private final ConversionService<?> conversionService;
    private final Map<CharSequence, Cookie> cookies;

    public NettyCookies(HttpHeaders nettyHeaders, ConversionService conversionService) {
        this.conversionService = conversionService;
        String value = nettyHeaders.get(HttpHeaderNames.COOKIE);
        if(value != null) {
            cookies = new LinkedHashMap<>();
            Set<io.netty.handler.codec.http.cookie.Cookie> nettyCookies = ServerCookieDecoder.LAX.decode(value);
            for (io.netty.handler.codec.http.cookie.Cookie nettyCookie : nettyCookies) {
                cookies.put(nettyCookie.name(), new NettyCookie(nettyCookie));
            }
        }
        else {
            cookies = Collections.emptyMap();
        }
    }

    @Override
    public Set<Cookie> getAll() {
        return new HashSet<>(cookies.values());
    }

    @Override
    public Optional<Cookie> findCookie(CharSequence name) {
        Cookie cookie = cookies.get(name);
        return  cookie != null ? Optional.of(cookie) : Optional.empty();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return findCookie(name).flatMap((cookie -> conversionService.convert(cookie.getValue(), requiredType)));
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        return findCookie(name).flatMap((cookie -> conversionService.convert(cookie.getValue(), requiredType.getType(), ConversionContext.of(requiredType))));
    }

    @Override
    public Collection<Cookie> values() {
        return Collections.unmodifiableCollection(cookies.values());
    }

    public static class NettyCookie implements Cookie {
        private final io.netty.handler.codec.http.cookie.Cookie nettyCookie;
        public NettyCookie(io.netty.handler.codec.http.cookie.Cookie nettyCookie) {
            this.nettyCookie = nettyCookie;
        }

        public NettyCookie(String name, String value) {
            Objects.requireNonNull(name, "Argument name cannot be null");
            Objects.requireNonNull(value, "Argument value cannot be null");

            this.nettyCookie = new DefaultCookie(name,value);
        }

        public io.netty.handler.codec.http.cookie.Cookie getNettyCookie() {
            return nettyCookie;
        }

        @Override
        public String getName() {
            return nettyCookie.name();
        }

        @Override
        public String getValue() {
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
        public Cookie setMaxAge(long maxAge) {
            nettyCookie.setMaxAge(maxAge);
            return this;
        }

        @Override
        public Cookie setValue(String value) {
            nettyCookie.setValue(value);
            return this;
        }

        @Override
        public Cookie setDomain(String domain) {
            nettyCookie.setDomain(domain);
            return this;
        }

        @Override
        public Cookie setPath(String path) {
            nettyCookie.setPath(path);
            return this;
        }

        @Override
        public Cookie setSecure(boolean secure) {
            nettyCookie.setSecure(secure);
            return this;
        }

        @Override
        public Cookie setHttpOnly(boolean httpOnly) {
            nettyCookie.setHttpOnly(httpOnly);
            return this;
        }

        @Override
        public int compareTo(Cookie o) {
            NettyCookie nettyCookie = (NettyCookie) o;
            return nettyCookie.nettyCookie.compareTo(this.nettyCookie);
        }
    }
}
