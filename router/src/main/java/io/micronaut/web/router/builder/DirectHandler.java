/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.direct.DirectExchange;

/**
 * Answers the requests of a direct route by commands on their exchange, see
 * {@link DirectRouteSpec#handle(DirectHandler)}: it checks the request and writes the response,
 * on the thread that received the request, without a request or a response object.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface DirectHandler {

    /**
     * @param exchange      The request and its response, valid while this runs
     * @param pathVariables The path variables of the matched route
     * @return Whether the handler answered the request; {@code false} declines it, whatever it
     *         wrote, and the request continues to the ordinary routes with its body untouched
     * @throws Exception if it fails: the request is answered with {@code 500}, whatever it wrote
     */
    boolean handle(DirectExchange exchange, PathVariables pathVariables) throws Exception;
}
