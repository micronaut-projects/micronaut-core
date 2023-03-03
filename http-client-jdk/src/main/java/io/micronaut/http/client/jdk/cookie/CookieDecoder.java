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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookies;

import java.util.Optional;

/**
 * Interface to allow cookie decoding.
 *
 * @since 4.0.0
 * @author Tim Yates
 */
public interface CookieDecoder extends Ordered {

    /**
     * Decode the cookies from the request.
     *
     * @param request the request
     * @return the cookies or empty if none
     */
    @NonNull
    Optional<Cookies> decode(HttpRequest<?> request);
}
