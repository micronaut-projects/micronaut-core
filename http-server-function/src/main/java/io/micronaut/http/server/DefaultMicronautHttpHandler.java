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
package io.micronaut.http.server;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.web.router.Router;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
public class DefaultMicronautHttpHandler implements MicronautHttpHandler,
    MicronautAsyncHttpHandler {

    private final RouteExecutor routeExecutor;
    private final Router router;
    private final ErrorResponseProcessor<?> errorResponseProcessor;

    public DefaultMicronautHttpHandler(final RouteExecutor routeExecutor,
                                       final Router router,
                                       final ErrorResponseProcessor<?> errorResponseProcessor) {
        this.routeExecutor = routeExecutor;
        this.router = router;
        this.errorResponseProcessor = errorResponseProcessor;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> handleAsync(final HttpRequest<?> request) {
        RequestLifecycle requestLifecycle = new RequestLifecycle(routeExecutor, request);

        return ReactiveExecutionFlow
            .fromFlow(requestLifecycle.normalFlow())
            .toPublisher();
    }

    @Override
    public HttpResponse<?> handle(final HttpRequest<?> request) {
        return Mono.from(handleAsync(request))
            .block();
    }
}
