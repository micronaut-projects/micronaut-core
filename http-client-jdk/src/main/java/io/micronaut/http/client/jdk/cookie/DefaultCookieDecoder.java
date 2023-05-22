/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk.cookie;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookies;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Default implementation of {@link CookieDecoder} that returns the cookies from the request.
 *
 * @since 4.0.0
 * @author Tim Yates
 */
@Singleton
@Experimental
@Internal
public class DefaultCookieDecoder implements CookieDecoder {

    @Override
    public Optional<Cookies> decode(HttpRequest<?> request) {
        return Optional.of(request.getCookies());
    }

    @Override
    public int getOrder() {
        return NettyCookieDecoder.ORDER + 1;
    }
}
