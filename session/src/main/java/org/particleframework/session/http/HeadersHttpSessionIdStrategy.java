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
package org.particleframework.session.http;

import org.particleframework.context.annotation.Requires;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MutableHttpHeaders;
import org.particleframework.http.MutableHttpResponse;
import org.particleframework.session.Session;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

/**
 * Implementation that uses common HTTP headers to resolve the {@link org.particleframework.session.Session} ID
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(property = "particle.session.http.header", notEquals = "false")
public class HeadersHttpSessionIdStrategy implements HttpSessionIdStrategy {

    private final String[] headerNames;

    public HeadersHttpSessionIdStrategy(HttpSessionConfiguration configuration) {
        this.headerNames = configuration.getHeaderNames();
        if(ArrayUtils.isEmpty(headerNames)) {
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
            if(!all.isEmpty()) {
                return all;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void encodeId(HttpRequest<?> request, MutableHttpResponse<?> response, Session session) {
        MutableHttpHeaders headers = response.getHeaders();
        if(session.isExpired()) {
            headers.add(headerNames[0], "");
        }
        else {
            headers.add(headerNames[0], session.getId());
        }
    }
}
