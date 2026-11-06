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
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpRequestWrapper
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CustomHttpMethod
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.MessageBodyHandlerRegistry
import io.micronaut.scheduling.executor.ExecutorSelector
import io.micronaut.web.router.DefaultUrlRouteInfo
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteInfo
import io.micronaut.web.router.UriRouteMatch
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A route declared with {@link CustomHttpMethod} is registered under its custom method name, and the
 * route info exposed by the router must report that name rather than {@link HttpMethod#CUSTOM}.
 */
class CustomHttpMethodRouteInfoSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'CustomHttpMethodRouteInfoSpec'])

    @Shared
    Router router = context.getBean(Router)

    void "uriRoutes reports the custom method name of a @CustomHttpMethod route"() {
        when:
        UriRouteInfo<Object, Object> route = router.uriRoutes()
                .filter { it.uriMatchTemplate.toPathString() == '/custom-method-route-info/report' }
                .findFirst()
                .orElseThrow()

        then:
        route.httpMethod == HttpMethod.CUSTOM
        route.httpMethodName == 'REPORT'
        route.toString().startsWith('REPORT /custom-method-route-info/report')
    }

    void "a matched @CustomHttpMethod route reports the custom method name"() {
        given: "the simple request implementation does not keep a custom method name, so supply it"
        HttpRequest<?> request = new HttpRequestWrapper<Object>(HttpRequest.create(HttpMethod.CUSTOM, '/custom-method-route-info/report')) {
            @Override
            String getMethodName() {
                'REPORT'
            }
        }

        when:
        UriRouteMatch<Object, Object> match = router.findClosest(request)

        then:
        match != null
        match.routeInfo.httpMethodName == 'REPORT'
    }

    void "standard methods still report their own name"() {
        when:
        UriRouteInfo<Object, Object> route = router.uriRoutes()
                .filter { it.uriMatchTemplate.toPathString() == '/custom-method-route-info/standard' && it.httpMethod == HttpMethod.GET }
                .findFirst()
                .orElseThrow()

        then:
        route.httpMethodName == 'GET'
    }

    void "a route info built without a method name reports the name of its HTTP method"() {
        given:
        UriRouteInfo<Object, Object> declared = router.uriRoutes()
                .filter { it.uriMatchTemplate.toPathString() == '/custom-method-route-info/standard' && it.httpMethod == HttpMethod.GET }
                .findFirst()
                .orElseThrow()

        when:
        UriRouteInfo<Object, Object> route = new DefaultUrlRouteInfo<Object, Object>(
                HttpMethod.GET,
                declared.uriMatchTemplate,
                StandardCharsets.UTF_8,
                declared.targetMethod,
                null,
                null,
                [],
                [],
                [],
                null,
                context.getBean(ConversionService),
                context.getBean(ExecutorSelector),
                context.getBean(MessageBodyHandlerRegistry))

        then:
        route.httpMethodName == 'GET'
        !route.implicitHead
    }

    @Requires(property = 'spec.name', value = 'CustomHttpMethodRouteInfoSpec')
    @Controller('/custom-method-route-info')
    static class CustomMethodController {

        @CustomHttpMethod(method = 'REPORT', value = '/report')
        String report() {
            'report'
        }

        @Get('/standard')
        String standard() {
            'standard'
        }
    }
}
