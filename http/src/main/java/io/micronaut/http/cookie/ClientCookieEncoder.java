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
package io.micronaut.http.cookie;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;

/**
 * Encodes a {@link Cookie} into a String. Typically used to set the {@link io.micronaut.http.HttpHeaders#COOKIE} value for example in an HTTP Client.
 * The cookie gets serialized by concatenating the cookie's name, the %x3D ("=") character, and the cookie's value.
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.4">Cookie Header</a>.
 * @author Sergio del Amo
 * @since 4.3.0
 */
@FunctionalInterface
public interface ClientCookieEncoder {
    /**
     * The default {@link ServerCookieEncoder} instance.
     */
    ClientCookieEncoder INSTANCE = SoftServiceLoader
            .load(ClientCookieEncoder.class)
            .firstOr("io.micronaut.http.cookie.DefaultClientCookieEncoder", ClientCookieEncoder.class.getClassLoader())
            .map(ServiceDefinition::load)
            .orElse(null);

    /**
     * Encodes a {@link Cookie} into a String. Typically used to set the {@link io.micronaut.http.HttpHeaders#COOKIE} value for example in an HTTP Client.
     * The cookie gets serialized by concatenating the cookie's name, the %x3D ("=") character, and the cookie's value.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.4">Cookie Header</a>.
     * @param cookie Cookie to encode
     * @return The cookie serialized into a string by concatenating the cookie's name, the %x3D ("=") character, and the cookie's value.
     */
    @NonNull
    String encode(@NonNull Cookie cookie);
}
