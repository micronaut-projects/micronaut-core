/*
 * Copyright 2017-2018 original authors
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
import java.util.regex.Pattern;

/**
 *
 * Implementation of {@link io.micronaut.http.util.RequestProcessor}.
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class RequestProcessorImpl implements RequestProcessor {

    /**
     * @param matcher A {@link RequestProcessorMatcher} implementation. Entity defining matching rules.
     * @param request The request
     * @return true if the request should be processed
     */
    public boolean shouldProcessRequest(RequestProcessorMatcher matcher, HttpRequest<?> request) {
        Optional<String> serviceId = request.getAttribute(HttpAttributes.SERVICE_ID.toString(), String.class);
        String uri = request.getUri().toString();
        return shouldProcessRequest(matcher, serviceId.orElse(null), uri);
    }

    /**
     *
     * @param matcher A {@link RequestProcessorMatcher} implementation. Entity defining matching rules.
     * @param serviceId The service id
     * @param uri The URI of the request being processed
     * @return true if the request should be processed
     */
    public boolean shouldProcessRequest(RequestProcessorMatcher matcher, String serviceId, String uri) {
        if (matcher.getServiceIdRegex() != null && serviceId != null) {
            Pattern pattern = Pattern.compile(matcher.getServiceIdRegex());
            if (pattern.matcher(serviceId).matches()) {
                return true;
            }
        }
        if (matcher.getUriRegex() != null && uri != null) {
            Pattern pattern = Pattern.compile(matcher.getUriRegex());
            if (pattern.matcher(uri).matches()) {
                return true;
            }
        }

        return false;
    }
}
