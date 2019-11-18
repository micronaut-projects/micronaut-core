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

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.server.cors.CorsFilter
import io.micronaut.http.server.cors.CorsOriginConfiguration
import io.micronaut.http.server.HttpServerConfiguration
import spock.lang.Specification

import static io.micronaut.http.HttpHeaders.*

class CorsFilterSpec extends Specification {

    CorsFilter buildCorsHandler(HttpServerConfiguration.CorsConfiguration config) {
        new CorsFilter(config ?: new HttpServerConfiguration.CorsConfiguration())
    }

    void "test handleRequest for non CORS request"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        headers.getOrigin() >> Optional.empty()
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        def result = corsHandler.handleRequest(request)

        then: "the request is passed through"
        !result.isPresent()
    }

    void "test handleRequest with no matching configuration"() {
        given:
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers

        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        def result = corsHandler.handleRequest(request)

        then: "the request is passed through because no configuration matches the origin"
        2 * headers.getOrigin() >> Optional.of('http://www.bar.com')
        !result.isPresent()
    }

    void "test handleRequest with regex matching configuration"() {
        given:
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers

        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = regex
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        def result = corsHandler.handleRequest(request)

        then: "the request is passed through because no configuration matches the origin"
        2 * headers.getOrigin() >> Optional.of(origin)
        !result.isPresent()

        where:
        regex                               | origin
        ['.*']                              | 'http://www.bar.com'
        ['^http://www\\.(foo|bar)\\.com$']  | 'http://www.bar.com'
        ['^http://www\\.(foo|bar)\\.com$']  | 'http://www.foo.com'
        ['.*bar$', '.*foo$']                | 'asdfasdf foo'
        ['.*bar$', '.*foo$']                | 'asdfasdf bar'
    }

    void "test handleRequest with disallowed method"() {
        given:
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers

        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        def result = corsHandler.handleRequest(request)

        then: "the request is rejected because the method is not in the list of allowedMethods"
        2 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        1 * request.getMethod() >> HttpMethod.POST
        result.isPresent()
        result.get().status == HttpStatus.FORBIDDEN
    }

    void "test handleRequest with disallowed header (not preflight)"() {
        given:
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers

        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)

        when:
        def result = corsHandler.handleRequest(request)

        then: "the request is passed through because allowed headers are only checked for preflight requests"
        2 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        1 * request.getMethod() >> HttpMethod.GET
        !result.isPresent()
        0 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.of(Argument.of(List,String)))
    }

    void "test preflight handleRequest with disallowed header"() {
        given:
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        originConfig.allowedHeaders = ['foo']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)
        request.getMethod() >> HttpMethod.OPTIONS

        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        def result = corsHandler.handleRequest(request)

        then: "the request is rejected because bar is not allowed"
        2 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        1 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, ConversionContext.of(HttpMethod.class)) >> Optional.of(HttpMethod.GET)
        1 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.of(Argument.of(List,String))) >> ['foo', 'bar']
        result.get().status == HttpStatus.FORBIDDEN
    }

    void "test preflight handleRequest with allowed header"() {
        given:
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        originConfig.allowedMethods = [HttpMethod.GET]
        originConfig.allowedHeaders = ['foo', 'bar']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)
        request.getMethod() >> HttpMethod.OPTIONS

        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        def result = corsHandler.handleRequest(request)

        then: "the request is successful"
        4 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        2 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, ConversionContext.of(HttpMethod.class)) >> Optional.of(HttpMethod.GET)
        2 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.of(Argument.of(List,String))) >> Optional.of(['foo'])
        result.get().status == HttpStatus.OK
    }

    void "test handleResponse when configuration not present"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers


        when:
        def result = corsHandler.handleRequest(request)

        then: "the response is not modified"
        2 * headers.getOrigin() >> Optional.of('http://www.bar.com')
        notThrown(NullPointerException)
        !result.isPresent()
    }

    void "test handleResponse for normal request"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        headers.getOrigin() >> Optional.of('http://www.foo.com')

        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        def result = corsHandler.handleRequest(request)

        then:
        !result.isPresent()

        when:
        MutableHttpResponse response = HttpResponse.ok()
        corsHandler.handleResponse(request, response)

        then: "the response is not modified"
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header' ]// Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
    }

    void "test handleResponse for preflight request"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        headers.getOrigin() >> Optional.of('http://www.foo.com')
        request.getMethod() >> HttpMethod.OPTIONS


        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        HttpResponse response = corsHandler.handleRequest(request).get()

        then: "the response is not modified"
        2 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.of(Argument.of(List,String))) >> Optional.of(['X-Header', 'Y-Header'])
        1 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, ConversionContext.of(HttpMethod.class)) >> Optional.of(HttpMethod.GET)
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
        def config = new HttpServerConfiguration.CorsConfiguration(singleHeader: true)
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = ['Foo-Header', 'Bar-Header']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsFilter corsHandler = buildCorsHandler(config)
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        headers.getOrigin() >> Optional.of('http://www.foo.com')
        request.getMethod() >> HttpMethod.OPTIONS


        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        HttpResponse response = corsHandler.handleRequest(request).get()

        then: "the response is not modified"
        2 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.of(Argument.of(List,String))) >> Optional.of(['X-Header', 'Y-Header'])
        1 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, ConversionContext.of(HttpMethod.class)) >> Optional.of(HttpMethod.GET)
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().get(ACCESS_CONTROL_EXPOSE_HEADERS) == 'Foo-Header,Bar-Header' // Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS) == 'X-Header,Y-Header' // Allow headers are echo'd from the request
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800' // Max age is set from config
    }
}
