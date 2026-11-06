package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.cors.CrossOrigin
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS

/**
 * The CORS filter caches the compiled origin regular expressions and the {@link CrossOrigin} configuration of each route.
 * Every request is sent several times, so the later ones are answered from the caches.
 */
class CorsFilterCachingSpec extends Specification {

    private static final String SPEC_NAME = "CorsFilterCachingSpec"
    private static final int REPEAT = 3

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            "spec.name"                                                        : SPEC_NAME,
            "micronaut.server.cors.enabled"                                    : true,
            "micronaut.server.cors.localhost-pass-through"                     : true,
            "micronaut.server.cors.configurations.global.allowed-origins-regex": '^https://global\\.com$'
    ])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Unroll
    void "#method #path from #origin is answered with the configuration of that route"(String method, String path, String origin,
                                                                                    String allowOrigin, String exposed, String credentials) {
        expect:
        (1..REPEAT).collect {
            HttpResponse<?> response = send(request(method, path, origin))
            [response.status(), response.header(ACCESS_CONTROL_ALLOW_ORIGIN), response.header(ACCESS_CONTROL_EXPOSE_HEADERS), response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS)]
        } == [[HttpStatus.OK, allowOrigin, exposed, credentials]] * REPEAT

        where:
        method | path                        | origin                | allowOrigin           | exposed  | credentials
        'GET'  | '/caching/one'              | 'https://one.com'     | 'https://one.com'     | null     | null
        'GET'  | '/caching/one'              | 'https://two.com'     | null                  | null     | null
        'GET'  | '/caching/one'              | 'https://one.com.evil'| null                  | null     | null
        'GET'  | '/caching/one'              | 'https://global.com'  | 'https://global.com'  | null     | null
        'GET'  | '/caching/two'              | 'https://two.com'     | 'https://two.com'     | null     | null
        'GET'  | '/caching/two'              | 'https://one.com'     | null                  | null     | null
        'GET'  | '/caching/shared-regex'     | 'https://one.com'     | 'https://one.com'     | null     | 'true'
        'GET'  | '/caching/shared-regex'     | 'https://two.com'     | null                  | null     | null
        'GET'  | '/caching/methods'          | 'https://get.com'     | 'https://get.com'     | 'X-Get'  | null
        'POST' | '/caching/methods'          | 'https://post.com'    | 'https://post.com'    | 'X-Post' | null
        'POST' | '/caching/methods'          | 'https://get.com'     | null                  | null     | null
        'GET'  | '/caching-class/inherited'  | 'https://class.com'   | 'https://class.com'   | null     | null
        'GET'  | '/caching-class/overridden' | 'https://class.com'   | 'https://class.com'   | null     | null
        'GET'  | '/caching-class/overridden' | 'https://method.com'  | 'https://method.com'  | null     | null
    }

    @Unroll
    void "preflight #requestMethod #path from #origin is answered with the configuration of the matching route"(String path, String origin, String requestMethod,
                                                                                                            HttpStatus status, String allowOrigin, String allowMethods) {
        expect:
        (1..REPEAT).collect {
            HttpResponse<?> response = send(preflight(path, origin, requestMethod))
            [response.status(), response.header(ACCESS_CONTROL_ALLOW_ORIGIN), response.header(ACCESS_CONTROL_ALLOW_METHODS)]
        } == [[status, allowOrigin, allowMethods]] * REPEAT

        where:
        path               | origin               | requestMethod | status               | allowOrigin          | allowMethods
        '/caching/one'     | 'https://one.com'    | 'GET'         | HttpStatus.OK        | 'https://one.com'    | 'GET'
        '/caching/one'     | 'https://global.com' | 'GET'         | HttpStatus.OK        | 'https://global.com' | 'GET'
        '/caching/two'     | 'https://two.com'    | 'GET'         | HttpStatus.OK        | 'https://two.com'    | 'GET'
        '/caching/two'     | 'https://two.com'    | 'POST'        | HttpStatus.FORBIDDEN | null                 | null
        '/caching/methods' | 'https://post.com'   | 'POST'        | HttpStatus.OK        | 'https://post.com'   | 'POST'
        '/caching/methods' | 'https://get.com'    | 'GET'         | HttpStatus.OK        | 'https://get.com'    | 'GET'
        '/caching/methods' | 'https://post.com'   | 'DELETE'      | HttpStatus.FORBIDDEN | null                 | null
    }

    private static MutableHttpRequest<?> request(String method, String path, String origin) {
        MutableHttpRequest<?> request = method == 'POST' ? HttpRequest.POST(path, 'body') : HttpRequest.GET(path)
        return request.header(HttpHeaders.ORIGIN, origin)
    }

    private static MutableHttpRequest<?> preflight(String path, String origin, String requestMethod) {
        return HttpRequest.OPTIONS(path)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestMethod)
    }

    private HttpResponse<?> send(MutableHttpRequest<?> request) {
        try {
            return client.toBlocking().exchange(request, String)
        } catch (HttpClientResponseException e) {
            return e.response
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/caching")
    static class CachingController {

        @CrossOrigin(allowedOriginsRegex = '^https://one\\.com$')
        @Get("/one")
        String one() {
            "one"
        }

        @CrossOrigin(allowedOriginsRegex = '^https://two\\.com$')
        @Get("/two")
        String two() {
            "two"
        }

        @CrossOrigin(allowedOriginsRegex = '^https://one\\.com$', allowCredentials = true)
        @Get("/shared-regex")
        String sharedRegex() {
            "shared-regex"
        }

        @CrossOrigin(value = "https://get.com", exposedHeaders = "X-Get")
        @Get("/methods")
        String get() {
            "get"
        }

        @CrossOrigin(value = "https://post.com", exposedHeaders = "X-Post")
        @Post("/methods")
        String post() {
            "post"
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @CrossOrigin("https://class.com")
    @Controller("/caching-class")
    static class CachingClassController {

        @Get("/inherited")
        String inherited() {
            "inherited"
        }

        @CrossOrigin("https://method.com")
        @Get("/overridden")
        String overridden() {
            "overridden"
        }
    }
}
