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
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.cookie.Cookies;

import java.net.URI;
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

    public NettyCookies(URI path, HttpHeaders nettyHeaders, ConversionService conversionService) {

        this.conversionService = conversionService;
        String value = nettyHeaders.get(HttpHeaderNames.COOKIE);
        if(value != null) {
            cookies = new LinkedHashMap<>();
            String pathStr = path.toString();
            Set<io.netty.handler.codec.http.cookie.Cookie> nettyCookies = ServerCookieDecoder.LAX.decode(value);
            for (io.netty.handler.codec.http.cookie.Cookie nettyCookie : nettyCookies) {
                String cookiePath = nettyCookie.path();
                if(cookiePath != null) {
                    if( pathStr.startsWith(cookiePath) ) {
                        cookies.put(nettyCookie.name(), new NettyCookie(nettyCookie));
                    }
                }
                else {
                    cookies.put(nettyCookie.name(), new NettyCookie(nettyCookie));
                }
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
        if(requiredType == Cookie.class || requiredType == Object.class) {
            return (Optional<T>) findCookie(name);
        }
        else {
            return findCookie(name).flatMap((cookie -> conversionService.convert(cookie.getValue(), requiredType)));
        }
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        return findCookie(name).flatMap((cookie -> conversionService.convert(cookie.getValue(), ConversionContext.of(requiredType))));
    }

    @Override
    public Collection<Cookie> values() {
        return Collections.unmodifiableCollection(cookies.values());
    }

}
