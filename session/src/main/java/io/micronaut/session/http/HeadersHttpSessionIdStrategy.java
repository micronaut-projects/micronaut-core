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
package io.micronaut.session.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.session.Session;
import io.micronaut.session.SessionSettings;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

/**
 * Implementation that uses common HTTP headers to resolve the {@link io.micronaut.session.Session} ID.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(property = SessionSettings.HTTP_HEADER_STRATEGY, notEquals = StringUtils.FALSE)
public class HeadersHttpSessionIdStrategy implements HttpSessionIdStrategy {

    private final String[] headerNames;

    /**
     * Constructor.
     *
     * @param configuration The HTTP session configuration
     */
    public HeadersHttpSessionIdStrategy(HttpSessionConfiguration configuration) {
        this.headerNames = configuration.getHeaderNames();
        if (ArrayUtils.isEmpty(headerNames)) {
            throw new ConfigurationException("At least one header name is required");
        }
    }

    /**
     * @return The header names to check
     */
    public String[] getHeaderNames() {
        return headerNames;
    }

    @Override
    public List<String> resolveIds(HttpRequest<?> message) {
        for (String headerName : headerNames) {
            List<String> all = message.getHeaders().getAll(headerName);
            if (!all.isEmpty()) {
                return all;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void encodeId(HttpRequest<?> request, MutableHttpResponse<?> response, Session session) {
        MutableHttpHeaders headers = response.getHeaders();
        if (session.isExpired()) {
            headers.add(headerNames[0], "");
        } else {
            headers.add(headerNames[0], session.getId());
        }
    }
}
