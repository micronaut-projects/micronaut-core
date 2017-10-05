package org.particleframework.http.server.netty.cors

import org.particleframework.core.type.Argument
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpMethod
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.MutableHttpResponse
import org.particleframework.http.cors.CorsHandler
import org.particleframework.http.cors.CorsOriginConfiguration
import org.particleframework.http.server.HttpServerConfiguration
import spock.lang.Specification

import static org.particleframework.http.HttpHeaders.*

class CorsHandlerSpec extends Specification {

    CorsHandler buildCorsHandler(HttpServerConfiguration.CorsConfiguration config) {
        new CorsHandler(config ?: new HttpServerConfiguration.CorsConfiguration())
    }

    void "test handleRequest for non CORS request"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        headers.getOrigin() >> Optional.empty()
        CorsHandler corsHandler = buildCorsHandler(config)

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
        CorsHandler corsHandler = buildCorsHandler(config)

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
        CorsHandler corsHandler = buildCorsHandler(config)

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
        CorsHandler corsHandler = buildCorsHandler(config)

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
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        def result = corsHandler.handleRequest(request)

        then: "the request is passed through because allowed headers are only checked for preflight requests"
        2 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        1 * request.getMethod() >> HttpMethod.GET
        !result.isPresent()
        0 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, Argument.of(List,String))
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
        CorsHandler corsHandler = buildCorsHandler(config)
        request.getMethod() >> HttpMethod.OPTIONS

        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        def result = corsHandler.handleRequest(request)

        then: "the request is rejected because bar is not allowed"
        2 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        1 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.class) >> Optional.of(HttpMethod.GET)
        1 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, Argument.of(List,String)) >> ['foo', 'bar']
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
        CorsHandler corsHandler = buildCorsHandler(config)
        request.getMethod() >> HttpMethod.OPTIONS

        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        def result = corsHandler.handleRequest(request)

        then: "the request is successful"
        4 * headers.getOrigin() >> Optional.of('http://www.foo.com')
        2 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.class) >> Optional.of(HttpMethod.GET)
        2 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, Argument.of(List,String)) >> Optional.of(['foo'])
        result.get().status == HttpStatus.OK
    }

    void "test handleResponse when configuration not present"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = ['http://www.foo.com']
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)
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
        CorsHandler corsHandler = buildCorsHandler(config)
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
        CorsHandler corsHandler = buildCorsHandler(config)
        HttpRequest request = Mock(HttpRequest)
        HttpHeaders headers = Mock(HttpHeaders)
        request.getHeaders() >> headers
        headers.getOrigin() >> Optional.of('http://www.foo.com')
        request.getMethod() >> HttpMethod.OPTIONS


        when:
        headers.contains(ACCESS_CONTROL_REQUEST_METHOD) >> true
        HttpResponse response = corsHandler.handleRequest(request).get()

        then: "the response is not modified"
        2 * headers.get(ACCESS_CONTROL_REQUEST_HEADERS, Argument.of(List,String)) >> Optional.of(['X-Header', 'Y-Header'])
        1 * headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.class) >> Optional.of(HttpMethod.GET)
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header'] // Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().getAll(ACCESS_CONTROL_ALLOW_HEADERS) == ['X-Header', 'Y-Header'] // Allow headers are echo'd from the request
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800' // Max age is set from config
    }

}
