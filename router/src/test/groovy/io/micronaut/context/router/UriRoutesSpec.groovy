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
package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CustomHttpMethod
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteInfo
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class UriRoutesSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'UriRoutesSpec'])

    @Shared
    Router router = context.getBean(Router)

    void "uriRoutes returns each route exactly once"() {
        given:
        List<UriRouteInfo<?, ?>> routes = router.uriRoutes().toList()

        expect:
        routes.size() == routes.toSet().size()
    }

    void "uriRoutes returns standard and custom method routes"() {
        given:
        List<String> routes = router.uriRoutes().toList()
                .findAll { it.uriMatchTemplate.toString().startsWith("/uri-routes") }
                .collect { it.targetMethod.methodName + " " + it.uriMatchTemplate }
                .sort()

        expect: "get has an implicit HEAD route alongside its GET route"
        routes == [
                "get /uri-routes/get",
                "get /uri-routes/get",
                "post /uri-routes/post",
                "report /uri-routes/report",
        ]
    }

    @Requires(property = "spec.name", value = "UriRoutesSpec")
    @Controller("/uri-routes")
    static class UriRoutesController {

        @Get("/get")
        String get() {
            "get"
        }

        @Post("/post")
        String post() {
            "post"
        }

        @CustomHttpMethod(method = "REPORT", value = "/report")
        String report() {
            "report"
        }
    }
}
