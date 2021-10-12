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
package io.micronaut.http.simple.cookies;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;

import java.util.*;

/**
 * Simple {@link Cookies} implementation.
 *
 * @author Graeme Rocher
 * @author Vladimir Orany
 * @since 1.0
 */
public class SimpleCookies implements Cookies {

    private final ConversionService<?> conversionService;
    private final Map<CharSequence, Cookie> cookies;

    /**
     * @param conversionService The conversion service
     */
    public SimpleCookies(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.cookies = new LinkedHashMap<>();
    }

    @Override
    public Set<Cookie> getAll() {
        return new HashSet<>(cookies.values());
    }

    @Override
    public Optional<Cookie> findCookie(CharSequence name) {
        Cookie cookie = cookies.get(name);
        return cookie != null ? Optional.of(cookie) : Optional.empty();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        if (requiredType == Cookie.class || requiredType == Object.class) {
            //noinspection unchecked
            return (Optional<T>) findCookie(name);
        } else {
            return findCookie(name).flatMap((cookie -> conversionService.convert(cookie.getValue(), requiredType)));
        }
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return findCookie(name).flatMap((cookie -> conversionService.convert(cookie.getValue(), conversionContext)));
    }

    @Override
    public Collection<Cookie> values() {
        return Collections.unmodifiableCollection(cookies.values());
    }

    /**
     * Put a new cookie.
     * @param name      the name of the cookie
     * @param cookie    the cookie itself
     * @return previous value for given name
     */
    public Cookie put(CharSequence name, Cookie cookie) {
        return this.cookies.put(name, cookie);
    }

    /**
     * Put a set of new cookies.
     * @param cookies   Map of cookie names and cookies
     */
    public void putAll(Map<CharSequence, Cookie> cookies) {
        this.cookies.putAll(cookies);
    }
}
