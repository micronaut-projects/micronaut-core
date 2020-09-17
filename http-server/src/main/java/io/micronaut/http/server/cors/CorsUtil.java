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
package io.micronaut.http.server.cors;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Utility methods for CORS.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class CorsUtil {

    /**
     * @param request The {@link HttpRequest} object
     * @return Return whether this request is a pre-flight request
     */
    static boolean isPreflightRequest(HttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        Optional<String> origin = headers.getOrigin();
        return origin.isPresent() && headers.contains(ACCESS_CONTROL_REQUEST_METHOD) && HttpMethod.OPTIONS == request.getMethod();
    }
}
