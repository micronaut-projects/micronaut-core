/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.originatingips;

import io.micronaut.http.HttpRequest;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the originating IP Addresses using the value of an HTTP Header.
 * @see <a href="https://en.wikipedia.org/wiki/X-Forwarded-For">X-Forwarded-For</a>
 * @author Sergio del Amo
 * @since 1.2.0
 */
@Singleton
public class HttpHeaderRequestIpAddressesResolver implements RequestIpAddressesResolver {

    private final HttpHeaderRequestIpAddressesResolverConfiguration configuration;

    /**
     *
     * @param configuration Configuration
     */
    public HttpHeaderRequestIpAddressesResolver(HttpHeaderRequestIpAddressesResolverConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    @Nonnull
    public List<String> requestIpAddresses(HttpRequest<?> request) {
        String value = request.getHeaders().get(configuration.getHeaderName());
        if (value == null) {
            return new ArrayList<>();
        }

        String[] arr = value.split(configuration.getDelimiter());

        return Stream.of(arr)
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
