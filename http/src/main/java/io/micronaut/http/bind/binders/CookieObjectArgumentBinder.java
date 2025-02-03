/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;

import java.util.Optional;

/**
 * Simple {@link Cookie} binder.
 *
 * @author Denis Stepanov
 * @since 4.8
 */
@Internal
public final class CookieObjectArgumentBinder implements RequestArgumentBinder<Cookie> {

    @Override
    public RequestArgumentBinder<Cookie> createSpecific(Argument<Cookie> argument) {
        String name = argument.getName();
        String fallback = NameUtils.hyphenate(name);
        return (context, source) -> {
            Cookies cookies = source.getCookies();
            Cookie cookie = cookies.get(name);
            if (cookie == null) {
                cookie = cookies.get(fallback);
            }
            Cookie finalCookie = cookie;
            return () -> finalCookie != null ? Optional.of(finalCookie) : Optional.empty();
        };
    }

    @Override
    public BindingResult<Cookie> bind(ArgumentConversionContext<Cookie> context, HttpRequest<?> source) {
        throw new IllegalStateException("Specific expected");
    }
}
