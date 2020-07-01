/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.util;

import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;

import javax.inject.Singleton;
import java.util.Optional;

/**
 *
 * Implementation of {@link OutgoingHttpRequestProcessor}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class OutgoingHttpRequestProcessorImpl implements OutgoingHttpRequestProcessor {

    /**
     * @param matcher A {@link OutgointRequestProcessorMatcher} implementation. Entity defining matching rules.
     * @param request The request
     * @return true if the request should be processed
     */
    @Override
    public boolean shouldProcessRequest(OutgointRequestProcessorMatcher matcher, HttpRequest<?> request) {
        Optional<String> serviceId = request.getAttribute(HttpAttributes.SERVICE_ID.toString(), String.class);
        String uri = request.getUri().toString();
        return shouldProcessRequest(matcher, serviceId.orElse(null), uri);
    }

    /**
     *
     * @param matcher A {@link OutgointRequestProcessorMatcher} implementation. Entity defining matching rules.
     * @param serviceId The service id
     * @param uri The URI of the request being processed
     * @return true if the request should be processed
     */
    public boolean shouldProcessRequest(OutgointRequestProcessorMatcher matcher, String serviceId, String uri) {
        if (matcher.getServiceIdPattern() != null && serviceId != null) {
            if (matcher.getServiceIdPattern().matcher(serviceId).matches()) {
                return true;
            }
        }
        if (matcher.getUriPattern() != null && uri != null) {
            if (matcher.getUriPattern().matcher(uri).matches()) {
                return true;
            }
        }

        return false;
    }
}
