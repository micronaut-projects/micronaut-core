package org.particleframework.http.cors

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
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is passed through"
        1 * request.isCorsRequest() >> false
        1 * requestHandler.continueRequest()
    }

    void "test handleRequest with no matching configuration"() {
        given:
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(['http://www.foo.com'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is passed through because no configuration matches the origin"
        1 * request.getHeader(ORIGIN) >> 'http://www.bar.com'
        1 * request.isCorsRequest() >> true
        1 * requestHandler.continueRequest()
    }

    void "test handleRequest with regex matching configuration"() {
        given:
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(regex)
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is passed through because no configuration matches the origin"
        1 * request.getHeader(ORIGIN) >> origin
        1 * request.isCorsRequest() >> true
        1 * requestHandler.continueRequest()

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
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(['http://www.foo.com'])
        originConfig.allowedMethods = Optional.of(['GET'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is rejected because the method is not in the list of allowedMethods"
        1 * request.getHeader(ORIGIN) >> 'http://www.foo.com'
        1 * request.getMethod() >> 'POST'
        1 * request.isCorsRequest() >> true
        1 * requestHandler.rejectRequest()
    }

    void "test handleRequest with disallowed header (not preflight)"() {
        given:
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(['http://www.foo.com'])
        originConfig.allowedMethods = Optional.of(['GET'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is passed through because allowed headers are only checked for preflight requests"
        1 * request.getHeader(ORIGIN) >> 'http://www.foo.com'
        1 * request.getMethod() >> 'GET'
        1 * request.isCorsRequest() >> true
        1 * requestHandler.continueRequest()
        0 * request.getHeaders(ACCESS_CONTROL_REQUEST_HEADERS)
    }

    void "test preflight handleRequest with disallowed header"() {
        given:
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(['http://www.foo.com'])
        originConfig.allowedMethods = Optional.of(['GET'])
        originConfig.allowedHeaders = Optional.of(['foo'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is rejected because bar is not allowed"
        1 * request.getHeader(ORIGIN) >> 'http://www.foo.com'
        1 * request.getMethod() >> 'GET'
        1 * request.isCorsRequest() >> true
        1 * requestHandler.rejectRequest()
        1 * request.isPreflightRequest() >> true
        1 * request.getHeaders(ACCESS_CONTROL_REQUEST_HEADERS) >> ['foo', 'bar']
    }

    void "test preflight handleRequest with allowed header"() {
        given:
        CorsRequestHandler requestHandler = Mock(CorsRequestHandler)
        CorsRequest request = Mock(CorsRequest)
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(['http://www.foo.com'])
        originConfig.allowedMethods = Optional.of(['GET'])
        originConfig.allowedHeaders = Optional.of(['foo', 'bar'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)

        when:
        corsHandler.handleRequest(request, requestHandler)

        then: "the request is successful"
        1 * request.getHeader(ORIGIN) >> 'http://www.foo.com'
        1 * request.getMethod() >> 'GET'
        1 * request.isCorsRequest() >> true
        1 * requestHandler.preflightSuccess()
        1 * request.isPreflightRequest() >> true
        1 * request.getHeaders(ACCESS_CONTROL_REQUEST_HEADERS) >> ['foo']
    }

    void "test handleResponse for non CORS request"() {
        given:
        CorsHandler corsHandler = buildCorsHandler(new HttpServerConfiguration.CorsConfiguration())
        CorsRequest request = Mock(CorsRequest)

        when:
        corsHandler.handleResponse(request, null)

        then: "the response is not modified"
        1 * request.isCorsRequest() >> false
        0 * request.getHeader(ORIGIN)
        notThrown(NullPointerException)
    }

    void "test handleResponse when configuration not present"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.allowedOrigins = Optional.of(['http://www.foo.com'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)
        CorsRequest request = Mock(CorsRequest)

        when:
        corsHandler.handleResponse(request, null)

        then: "the response is not modified"
        1 * request.getHeader(ORIGIN) >> 'http://www.bar.com'
        1 * request.isCorsRequest() >> true
        notThrown(NullPointerException)
    }

    void "test handleResponse for normal request"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = Optional.of(['Foo-Header', 'Bar-Header'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)
        CorsRequest request = Mock(CorsRequest)
        CorsResponse response = Mock(CorsResponse)

        when:
        corsHandler.handleResponse(request, response)

        then: "the response is not modified"
        1 * request.getHeader(ORIGIN) >> 'http://www.foo.com'
        1 * request.isCorsRequest() >> true
        1 * request.isPreflightRequest() >> false
        1 * response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, 'http://www.foo.com') // The origin is echo'd
        1 * response.setHeader(VARY, 'Origin') // The vary header is set
        1 * response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, 'Foo-Header') // Expose headers are set from config
        1 * response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, 'Bar-Header') // Expose headers are set from config
        1 * response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, 'true') // Allow credentials header is set
    }

    void "test handleResponse for preflight request"() {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration()
        CorsOriginConfiguration originConfig = new CorsOriginConfiguration()
        originConfig.exposedHeaders = Optional.of(['Foo-Header', 'Bar-Header'])
        config.configurations = new LinkedHashMap<String, CorsOriginConfiguration>()
        config.configurations.put('foo', originConfig)
        CorsHandler corsHandler = buildCorsHandler(config)
        CorsRequest request = Mock(CorsRequest)
        CorsResponse response = Mock(CorsResponse)

        when:
        corsHandler.handleResponse(request, response)

        then: "the response is not modified"
        1 * request.getHeader(ORIGIN) >> 'http://www.foo.com'
        1 * request.getMethod() >> 'GET'
        1 * request.isCorsRequest() >> true
        1 * request.isPreflightRequest() >> true
        1 * request.getHeaders(ACCESS_CONTROL_REQUEST_HEADERS) >> ['X-Header', 'Y-Header']
        1 * response.setHeader(ACCESS_CONTROL_ALLOW_METHODS, 'GET')
        1 * response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, 'http://www.foo.com') // The origin is echo'd
        1 * response.setHeader(VARY, 'Origin') // The vary header is set
        1 * response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, 'Foo-Header') // Expose headers are set from config
        1 * response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, 'Bar-Header') // Expose headers are set from config
        1 * response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, 'true') // Allow credentials header is set
        1 * response.addHeader(ACCESS_CONTROL_ALLOW_HEADERS, 'X-Header') // Allow headers are echo'd from the request
        1 * response.addHeader(ACCESS_CONTROL_ALLOW_HEADERS, 'Y-Header') // Allow headers are echo'd from the request
        1 * response.setHeader(ACCESS_CONTROL_MAX_AGE, '1800') // Max age is set from config
    }

}
