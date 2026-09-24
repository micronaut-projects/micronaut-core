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
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.web.router.MethodBasedRouteInfo
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.exceptions.DuplicateRouteException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Among routes with the same literal length and number of variables, the route with fewer
 * variables constrained by a regular expression is more specific.
 */
class PatternVariableSpecificitySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'PatternVariableSpecificitySpec'])

    @Shared
    Router router = context.getBean(Router)

    @Unroll
    void "#path is routed to #method"() {
        when:
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path))

        then:
        methodName(match) == method

        and: "the same selection among all candidates"
        router.findAllClosest(HttpRequest.GET(path)).collect { methodName(it) } == [method]

        where:
        path          | method
        "/sel/1"      | "plain"
        "/sel/a/b"    | "anyPath"
        "/sel2/1/2"   | "plainPair"
        "/sel2/1/2/3" | "patternPair"
        "/sel3/1/2"   | "onePattern"
    }

    void "routes that tie on all three keys stay ambiguous"() {
        when:
        router.findClosest(HttpRequest.GET("/tie/1"))

        then:
        thrown(DuplicateRouteException)
    }

    private static String methodName(UriRouteMatch<?, ?> match) {
        ((MethodBasedRouteInfo<?, ?>) match.routeInfo).targetMethod.methodName
    }

    @Controller
    @Requires(property = 'spec.name', value = 'PatternVariableSpecificitySpec')
    static class SelectionController {
        @Get("/sel/{id}")
        String plain(String id) { id }

        @Get("/sel/{id:.+}")
        String anyPath(String id) { id }

        @Get("/sel2/{a}/{b}")
        String plainPair(String a, String b) { a + b }

        @Get("/sel2/{a}/{b:.+}")
        String patternPair(String a, String b) { a + b }

        @Get("/sel3/{a:.+}/{b:.+}")
        String twoPatterns(String a, String b) { a + b }

        @Get("/sel3/{a}/{b:.+}")
        String onePattern(String a, String b) { a + b }

        @Get("/tie/{id:.+}")
        String tieA(String id) { id }

        @Get("/tie/{id:[0-9]+}")
        String tieB(String id) { id }
    }
}
