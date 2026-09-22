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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

import java.util.concurrent.CompletionStage;

/**
 * A route handler that reads a submitted form as it arrives, part by part, with
 * {@link FormParts#forEach}: large files can be streamed to their destination without buffering
 * the form. The executor is selected like for a controller method returning a
 * {@link CompletionStage}, so with automatic thread selection it runs on the event loop and must
 * not block.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see RouteBuilder#handleFormStream(io.micronaut.http.HttpMethod, String, StreamingFormRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface StreamingFormRequestHandler {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param parts         The parts of the form, read as the handler consumes them
     * @return The response, completed later
     */
    CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, FormParts parts);
}
