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
package io.micronaut.docs.server.limit;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.limit.RequestLimit;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
// end::imports[]

@Requires(property = "spec.name", value = "RequestLimitTest")
// tag::clazz[]
@Singleton
public class LimitedReportRoutes implements HttpRoutes {

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.GET("/limited-reports/functional/{id}")
            .attribute(RequestLimit.ATTRIBUTE, "reports") // <1>
            .handle((request, pathVariables) -> HttpResponse.ok("report " + pathVariables.getString("id")));
    }
}
// end::clazz[]
