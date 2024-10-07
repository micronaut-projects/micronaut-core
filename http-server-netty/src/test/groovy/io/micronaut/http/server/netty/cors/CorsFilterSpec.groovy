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
package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.cors.CorsFilter
import io.micronaut.http.server.cors.CorsOriginConfiguration
import io.micronaut.http.server.util.HttpHostResolver
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.RouteMatch
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import org.apache.http.client.utils.URIBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_MAX_AGE
import static io.micronaut.http.HttpHeaders.VARY

class CorsFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CorsFilterSpec'])

    void "non CORS request is passed through"() {
        given:
        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration()
        CorsFilter corsHandler = buildCorsHandler(config)
        HttpRequest request = createRequest(null as String)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then: "the request is passed through"
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
        response.headers.names().isEmpty()
    }

    void "request with origin and no matching configuration"() {
        given:
        String origin = 'http://www.bar.com'
        HttpRequest request = createRequest(origin)
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then: "the request is passed through because no configuration matches the origin"
        HttpStatus.OK == response.status()
        response.headers.names().isEmpty()
    }

    @Unroll
    void "regex matching configuration"(String regex, String origin) {
        given:
        HttpRequest request = createRequest(origin)
        request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class) >> Optional.empty()

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOriginsRegex = regex
        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 3
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }
        response.headers.find { it.key == 'Vary' }
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }.value == [origin]
        response.headers.find { it.key == 'Vary' }.value == ['Origin']
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }.value == [StringUtils.TRUE]

        where:
        regex                               | origin
        '.*'                                | 'http://www.bar.com'
        '^http://www\\.(foo|bar)\\.com$'    | 'http://www.bar.com'
        '^http://www\\.(foo|bar)\\.com$'    | 'http://www.foo.com'
        '.*(bar|foo)$'                      | 'asdfasdf foo'
        '.*(bar|foo)$'                      | 'asdfasdf bar'
        '.*(bar|foo)$'                      | 'http://asdfasdf.foo'
        '.*(bar|foo)$'                      | 'http://asdfasdf.bar'
    }

    void "test handleRequest with disallowed method"() {
        given:
        String origin = 'http://www.foo.com'
        HttpRequest request = createRequest(origin)

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.FORBIDDEN == response.status()
        response.headers.names().isEmpty()
    }

    void "with disallowed header (not preflight) the request is passed through because allowed headers are only checked for preflight requests"() {
        given:
        String origin = 'http://www.foo.com'
        HttpRequest request = createRequest(origin)
        request.getMethod() >> HttpMethod.GET

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 3
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }
        response.headers.find { it.key == 'Vary' }
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }.value == ['http://www.foo.com']
        response.headers.find { it.key == 'Vary' }.value == ['Origin']
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }.value == [StringUtils.TRUE]
    }

    void "test preflight handleRequest with disallowed header"() {
        given:
        String origin = 'http://www.foo.com'
        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.of(origin)
            getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, _) >> Optional.of(HttpMethod.GET)
            get(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, _) >> Optional.of(['foo', 'bar'])
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
        }
        HttpRequest request = createRequest(headers)
        request.getMethod() >> HttpMethod.OPTIONS
        request.getUri() >> new URIBuilder( '/example' ).build()
        List<UriRouteMatch<?,?>> routes = embeddedServer.getApplicationContext().getBean(Router).findAny(request.getUri().toString(), request).collect(Collectors.toList())

        request.getAttribute(HttpAttributes.AVAILABLE_HTTP_METHODS, _) >> Optional.of(routes.stream().map(route->route.getHttpMethod()).collect(Collectors.toList()))

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        originConfig.allowedHeaders = ['foo']

        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then: "the request is rejected because bar is not allowed"
        HttpStatus.FORBIDDEN == response.status()
    }

    void "test preflight with allowed header"() {
        given:
        String origin = 'http://www.foo.com'

        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.of(origin)
            getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, _) >> Optional.of(HttpMethod.GET)
            get(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, _) >> Optional.of(['foo'])
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
        }
        HttpRequest request = createRequest(headers)
        request.getMethod() >> HttpMethod.OPTIONS
        request.getUri() >> new URIBuilder( '/example' ).build()
        List<UriRouteMatch<?,?>> routes = embeddedServer.getApplicationContext().getBean(Router).
                findAny(request)
        request.getAttribute(HttpAttributes.AVAILABLE_HTTP_METHODS, _) >> Optional.of(routes.stream().map(route->route.getHttpMethod()).collect(Collectors.toList()))

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        originConfig.allowedHeaders = ['foo', 'bar']

        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)


        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 6
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }
        response.headers.find { it.key == 'Vary' }
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }
        response.headers.find { it.key == 'Access-Control-Allow-Methods' }
        response.headers.find { it.key == 'Access-Control-Allow-Headers' }
        response.headers.find { it.key == 'Access-Control-Max-Age' }
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }.value == ['http://www.foo.com']
        response.headers.find { it.key == 'Vary' }.value == ['Origin']
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }.value == [StringUtils.TRUE]
        response.headers.find { it.key == 'Access-Control-Allow-Methods' }.value == ['GET']
        response.headers.find { it.key == 'Access-Control-Allow-Headers' }.value == ['foo']
        response.headers.find { it.key == 'Access-Control-Max-Age' }.value == ['1800']
    }

    void "test handleResponse when configuration not present"() {
        given:
        String origin = 'http://www.bar.com'
        HttpServerConfiguration.CorsConfiguration config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        config.setConfigurations([foo: originConfig])
        CorsFilter corsHandler = buildCorsHandler(config)
        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.of(origin)
        }
        HttpRequest request = Stub(HttpRequest) {
            getHeaders() >> headers
        }

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        notThrown(NullPointerException)
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()

        and:
        !response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN)
        !response.getHeaders().get(VARY)
        !response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS)
        !response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS)
        !response.getHeaders().get(ACCESS_CONTROL_MAX_AGE)
    }

    void "verify behaviour for normal request"() {
        given:
        String origin = 'http://www.foo.com'
        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.of(origin)
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
        }
        HttpRequest request = createRequest(headers)

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']

        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 5
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header' ]// Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800'
    }

    void "test handleResponse for preflight request"() {
        given:
        HttpHeaders headers = Stub(HttpHeaders) {
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
            get(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, _) >> Optional.of(['X-Header', 'Y-Header'])
            getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, _) >> Optional.of(HttpMethod.GET)
            getOrigin() >> Optional.of('http://www.foo.com')
        }
        URI uri = new URIBuilder('/example').build()
        HttpRequest request = Stub(HttpRequest) {
            getHeaders() >> headers
            getMethod() >> HttpMethod.OPTIONS
            getUri() >> uri
            getOrigin() >> headers.getOrigin()
        }
        List<UriRouteMatch<?,?>> routes = embeddedServer.getApplicationContext().getBean(Router).
                findAny(uri.toString(), request)
                .collect(Collectors.toList())
        request.getAttribute(HttpAttributes.AVAILABLE_HTTP_METHODS, _) >> Optional.of(routes.stream().map(route -> route.getHttpMethod()).collect(Collectors.toList()))

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']
        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 7
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header'] // Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().getAll(ACCESS_CONTROL_ALLOW_HEADERS) == ['X-Header', 'Y-Header'] // Allow headers are echo'd from the request
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800' // Max age is set from config
    }

    void "test handleResponse for preflight request with single header"() {
        given:
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']

        HttpServerConfiguration.CorsConfiguration config = new HttpServerConfiguration.CorsConfiguration(singleHeader: true, enabled: true)
        config.setConfigurations([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)

        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.of('http://www.foo.com')
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
            get(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, _) >> Optional.of(['X-Header', 'Y-Header'])
            getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, _) >> Optional.of(HttpMethod.GET)
        }
        URI uri = new URIBuilder( '/example' ).build()
        HttpRequest request = Stub(HttpRequest) {
            getHeaders() >> headers
            getMethod() >> HttpMethod.OPTIONS
            getUri() >> uri
            getOrigin() >> headers.getOrigin()
        }
        List<UriRouteMatch<?,?>> routes = embeddedServer.getApplicationContext().getBean(Router).
                findAny(request)
        request.getAttribute(HttpAttributes.AVAILABLE_HTTP_METHODS, _) >> Optional.of(routes.stream().map(route->route.getHttpMethod()).collect(Collectors.toList()))

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()

        then: "the response is not modified"
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().get(ACCESS_CONTROL_EXPOSE_HEADERS) == 'Foo-Header,Bar-Header' // Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS) == 'X-Header,Y-Header' // Allow headers are echo'd from the request
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800' // Max age is set from config
    }

    void "test preflight handleRequest on route that doesn't exists"() {
        given:
        String origin = 'http://www.foo.com'
        HttpHeaders headers = Stub(HttpHeaders) {
            getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, _) >> Optional.of(HttpMethod.GET)
            getOrigin() >> Optional.of(origin)
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
        }
        URI uri = new URIBuilder( '/doesnt-exists-route' ).build()
        HttpRequest request = Stub(HttpRequest) {
            getHeaders() >> headers
            getUri() >> uri
            getMethod() >> HttpMethod.OPTIONS
        }
        List<UriRouteMatch<?,?>> routes = embeddedServer.getApplicationContext().getBean(Router).
                findAny(uri.toString(), request)
                .collect(Collectors.toList())
        request.getAttribute(HttpAttributes.AVAILABLE_HTTP_METHODS, _) >> Optional.of(routes.stream().map(route->route.getHttpMethod()).collect(Collectors.toList()))

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        originConfig.allowedHeaders = ['foo', 'bar']

        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
    }

    void "test preflight handleRequest on route that does exist but doesn't handle requested HTTP Method"() {
        given:

        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']

        HttpServerConfiguration.CorsConfiguration config = enabledCorsConfiguration([foo: originConfig])

        CorsFilter corsHandler = buildCorsHandler(config)

        String origin = 'http://www.foo.com'
        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.of(origin)
            getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, _) >> Optional.of(HttpMethod.POST)
            contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) >> true
        }
        URI uri = new URIBuilder( '/example' ).build()
        HttpRequest request = Stub(HttpRequest) {
            getHeaders() >> headers
            getMethod() >> HttpMethod.OPTIONS
            getUri() >> uri
        }

        List<UriRouteMatch<?,?>> routes = embeddedServer.getApplicationContext().getBean(Router).
                findAny(request)
        request.getAttribute(HttpAttributes.AVAILABLE_HTTP_METHODS, _) >> Optional.of(routes.stream().map(route->route.getHttpMethod()).collect(Collectors.toList()))

        when:
        Optional<MutableHttpResponse<?>> result = filterOk(corsHandler, request)

        then:
        result.isPresent()

        when:
        MutableHttpResponse<?> response = result.get()

        then:
        HttpStatus.OK == response.status()
    }

    @Requires(property = "spec.name", value = "CorsFilterSpec")
    @Controller
    static class TestController{

        @Get("/example")
        String example() { return "Example"}
    }

    private HttpRequest<?> createRequest(String originHeader) {
        HttpHeaders headers = Stub(HttpHeaders) {
            getOrigin() >> Optional.ofNullable(originHeader)
        }
        createRequest(headers)
    }

    private HttpRequest<?> createRequest(HttpHeaders headers) {
        Stub(HttpRequest) {
            getHeaders() >> headers

            getOrigin() >> headers.getOrigin()
        }
    }

    private Optional<HttpResponse<?>> filterOk(CorsFilter filter, HttpRequest<?> req) {
        def earlyResponse = filter.filterRequest(req)
        if (earlyResponse != null) {
            return Optional.of(earlyResponse)
        }
        MutableHttpResponse<?> response = HttpResponse.ok()
        filter.filterResponse(req, response)
        return Optional.of(response)
    }

    private HttpServerConfiguration.CorsConfiguration enabledCorsConfiguration(Map<String, CorsOriginConfiguration> corsConfigurationMap = null) {
        HttpServerConfiguration.CorsConfiguration config = new HttpServerConfiguration.CorsConfiguration() {
            @Override
            boolean isEnabled() {
                true
            }
        }
        if (corsConfigurationMap != null) {
            config.setConfigurations(corsConfigurationMap)
        }
        config
    }

    private CorsFilter buildCorsHandler(HttpServerConfiguration.CorsConfiguration config) {
        new CorsFilter(config ?: enabledCorsConfiguration(), new HttpHostResolver() {
            @Override
            String resolve(@Nullable HttpRequest request) {
                return "http://micronautexample.com";
            }
        })
    }
}
