/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.web.router.DefaultRouteBuilder
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.version.DefaultVersionProvider
import io.micronaut.web.router.version.RouteVersionFilter
import io.micronaut.web.router.version.resolution.HeaderVersionResolver
import io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration
import io.micronaut.web.router.version.resolution.ParameterVersionResolver
import io.micronaut.web.router.version.resolution.ParameterVersionResolverConfiguration
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class DefaultVersionedUrlFilterSpec extends Specification {

    @Shared ApplicationContext context

    List<UriRouteMatch<Object, Object>> routes

    def strategies = [new HeaderVersionResolver(
            new HeaderVersionResolverConfiguration().with {
                names = ["API-VERSION"]
                it
            })]

    def setup() {
        context = ApplicationContext.run("test")
        def controller = new VersionedController()
        context.registerSingleton(controller)

        def router = new DefaultRouter(new DefaultRouteBuilder(context) {
            {
                GET("/versioned/hello", controller, "helloV1")
                GET("/versioned/hello", controller, "helloV2")
                GET("/versioned/hello", controller, "duplicatedHelloV2")
                GET("/versioned/hello", controller, "hello")
            }
        })
        routes = router.find(HttpMethod.GET, "/versioned/hello", null).collect(Collectors.toList())
    }

    def cleanup() {
        context.close()
    }

    def "should extract header version from request"() {
        when:
        def strategy = new HeaderVersionResolver(
                new HeaderVersionResolverConfiguration().with {
                    names = [specifiedHeader]
                    it
                }
        )
        def request = HttpRequest.GET("/test").header(exactHeader, "1")
        then:
        result == strategy.resolve(request)
        where:
        specifiedHeader | exactHeader   || result
        "API-VERSION"   | "API-VERSION" || Optional.of("1")
        "API-VERSION"   | "VERSION"     || Optional.empty()
    }

    def "should extract parameter version from request"() {
        when:
        def strategy = new ParameterVersionResolver(
                new ParameterVersionResolverConfiguration().with {
                    names = [specifiedParameter]
                    it
                }
        )
        def request = HttpRequest.GET("/test").with {
            parameters.add(exactParameter, "1")
            it
        }
        then:
        result == strategy.resolve(request)
        where:
        specifiedParameter | exactParameter || result
        "version"          | "version"      || Optional.of("1")
        "version"          | "vrsn"         || Optional.empty()
    }

    def "should extract parameter from multiple provided"() {
        when:
        def strategy = new ParameterVersionResolver(
                new ParameterVersionResolverConfiguration().with {
                    names = specifiedParameters
                    it
                }
        )
        def request = HttpRequest.GET("/test").with {
            parameters.add(exactParameter, "1")
            it
        }
        then:
        result == strategy.resolve(request)
        where:
        specifiedParameters      | exactParameter || result
        ["x-version", "version"] | "version"      || Optional.of("1")
        ["x-version", "version"] | "vrsn"         || Optional.empty()
        []                       | "vrsn"         || Optional.empty()
    }

    def "should return initial routes ignoring version"() {
        when:
        def strategies = []
        def handler = new RouteVersionFilter(strategies, null)
        def request = HttpRequest.GET("/versioned/hello")

        then:
        routes.stream().filter(handler.filter(request)).collect(Collectors.toList()) == routes
    }

    def "should return initial versions due to header provided"() {
        when:
        def handler = new RouteVersionFilter(strategies, null)
        def request = HttpRequest.GET("/versioned/hello")

        then:
        routes.stream().filter(handler.filter(request)).collect(Collectors.toList()) == routes
    }

    def "should return exact route for header version"() {
        when:
        def handler = new RouteVersionFilter(strategies, null)
        def request = HttpRequest.GET("/versioned/hello").header("API-VERSION", "1")
        def matches = routes.stream().filter(handler.filter(request)).collect(Collectors.toList())

        then:
        matches.size() == 1
        matches.get(0).getExecutableMethod().methodName == "helloV1"
    }

    def "should return duplicating routes for header version"() {
        when:
        def handler = new RouteVersionFilter(strategies, null)
        def request = HttpRequest.GET("/versioned/hello").header("API-VERSION", "2")
        def matches = routes.stream().filter(handler.filter(request)).collect(Collectors.toList())

        then:
        matches.size() == 2
    }

    def "should return only matched versions after using all version resolvers"() {
        when:
        def strategies = [
                new ParameterVersionResolver(
                    new ParameterVersionResolverConfiguration().with {
                        names = ["version"]
                        it
                    }
                ),
                new HeaderVersionResolver(
                    new HeaderVersionResolverConfiguration().with {
                        names = ["API-VERSION"]
                        it
                    })
        ]
        def handler = new RouteVersionFilter(strategies, null)
        def request = HttpRequest.GET("/versioned/hello").header("API-VERSION", "2")
        def matches = routes.stream().filter(handler.filter(request)).collect(Collectors.toList())
        then:
        matches.size() == 2
    }

    void "test default version configuration"() {
        when:
        DefaultVersionProvider defaultVersionProvider = Stub(DefaultVersionProvider) {
            resolveDefaultVersion() >> '2'
        }
        def handler = new RouteVersionFilter(strategies, defaultVersionProvider)
        def request = HttpRequest.GET("/versioned/hello")
        def matches = routes.stream().filter(handler.filter(request)).collect(Collectors.toList())

        then:
        //only the specified version routes that don't match the default are filtered out
        matches.size() == 3
        matches.stream()
                .map({r -> r.getExecutableMethod().methodName })
                .noneMatch({mn -> mn.equals("helloV1")})
    }

}
