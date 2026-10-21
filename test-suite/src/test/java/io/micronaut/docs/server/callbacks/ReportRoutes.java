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
package io.micronaut.docs.server.callbacks;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
// end::imports[]

@Requires(property = "spec.name", value = "ReportRoutesTest")
// tag::clazz[]
@Singleton
public class ReportRoutes implements HttpRoutes {
    private final Reports reports;

    public ReportRoutes(Reports reports) {
        this.reports = reports;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.GET("/reports/{id}").handleAsync((request, pathVariables) -> {
            String id = pathVariables.getString("id");
            CompletableFuture<String> report = reports.generate(id);
            if (request instanceof ServerHttpRequest<?> serverRequest) {
                serverRequest.whenAbandoned(() -> report.cancel(true)); // <1>
                serverRequest.whenResponseCompleted(failure -> reports.completed(id, failure)); // <2>
            }
            return report.thenApply(text -> HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE));
        });
    }
}
// end::clazz[]
