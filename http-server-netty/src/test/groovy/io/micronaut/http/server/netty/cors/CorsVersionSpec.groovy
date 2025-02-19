package io.micronaut.http.server.netty.cors

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.cors.CrossOrigin
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "CorsVersionSpec")
@Property(name = "micronaut.server.cors.enabled", value = StringUtils.TRUE)
@Property(name = "micronaut.router.versioning.enabled", value = StringUtils.TRUE)
@Property(name = "micronaut.router.versioning.default-version", value = "1")
@Property(name = "micronaut.router.versioning.header.enabled", value = StringUtils.TRUE)
@Property(name = "micronaut.router.versioning.header.names", value = "x-api-version")
@MicronautTest
class CorsVersionSpec extends Specification {
    @Inject
    @Client("/")
    HttpClient httpClient

    void "verify versioned routes behaved as expected"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()
        HttpRequest<?> request = createRequest("/common", "1")

        when:
        HttpResponse<Map<String, String>> rsp = client.exchange(request, Argument.mapOf(String.class, String.class))

        then:
        assertResponse(rsp, "common from v1")

        when:
        request = createRequest("/common", null)
        rsp = client.exchange(request, Argument.mapOf(String.class, String.class))

        then:
        assertResponse(rsp, "common from v1")

        when:
        request = createRequest("/common", "2")
        rsp = client.exchange(request, Argument.mapOf(String.class, String.class))
        then:
        assertResponse(rsp, "common from v2")

        when:
        request = createRequest("/new", "2")
        rsp = client.exchange(request, Argument.mapOf(String.class, String.class))

        then:
        assertResponse(rsp, "new from v2")
    }

    void "preflight for version routed which does not have a matching default version"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        MutableHttpRequest<?> request = HttpRequest.OPTIONS("/new")
        preflightHeaders("x-api-version", false).each { k, v -> request.header(k, v)}
        client.exchange(request)

        then:
        noExceptionThrown()
    }

    void "preflight for version routed without version header"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        MutableHttpRequest<?> request = HttpRequest.OPTIONS("/common")
        preflightHeaders(null, false).each { k, v -> request.header(k, v)}
        client.exchange(request)

        then:
        noExceptionThrown()

        when:
        request = HttpRequest.OPTIONS("/new")
        preflightHeaders(null, false).each { k, v -> request.header(k, v)}
        client.exchange(request)

        then:
        HttpClientResponseException ex = thrown()
        ex.status == HttpStatus.FORBIDDEN
    }

    void "preflight for version routed from private network"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        MutableHttpRequest<?> request = HttpRequest.OPTIONS("/common")
        preflightHeaders(null, true).each { k, v -> request.header(k, v)}
        client.exchange(request)

        then:
        noExceptionThrown()

        when:
        request = HttpRequest.OPTIONS("/new-not-allowed-from-private")
        preflightHeaders(null, true).each { k, v -> request.header(k, v)}
        client.exchange(request)

        then:
        HttpClientResponseException ex = thrown()
        ex.status == HttpStatus.FORBIDDEN
    }

    static Map<String, String> preflightHeaders(String accessControlRequestHeaders, boolean accessControlRequestPrivateNetwork) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "localhost:8080")
        headers.put("Sec-Fetch-Site", "same-site")
        headers.put("Accept-Encoding", "gzip, deflate")
        headers.put("Access-Control-Request-Method", "GET")
        headers.put("Sec-Fetch-Mode", "cors")
        headers.put("Accept-Language", "en-GB,en;q=0.9")
        headers.put("Origin", "http://localhost:8000")
        if (accessControlRequestHeaders) {
            headers.put("Access-Control-Request-Headers", accessControlRequestHeaders)
        }
        if (accessControlRequestPrivateNetwork) {
            headers.put("Access-Control-Request-Private-Network", "true")
        }
        headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5.2 Safari/605.1.15")
        headers.put("Referer", "http://localhost:8000/")
        headers.put("Content-Length", "0")
        headers.put("Connection", "keep-alive")
        headers.put("Sec-Fetch-Dest", "empty")
        headers.put("Accept", "*/*")
        headers
    }

    HttpRequest<?> createRequest(String path, String apiVersion) {
        return apiVersion == null ?
                HttpRequest.GET(path) :
                HttpRequest.GET(path).header("X-API-VERSION", apiVersion);
    }

    void assertResponse(HttpResponse<Map<String, String>> rsp, String expected) {
        assert HttpStatus.OK == rsp.getStatus()
        Map<String, String> body = rsp.body()
        assert body
        assert 1 == body.keySet().size()
        assert expected == body.get("message")
    }

    @Requires(property = "spec.name", value = "CorsVersionSpec")
    @Controller
    static class TestController {
        public static final String MESSAGE = "message";

        @Get( "/common")
        @Version("1")
        Map<String, String> commonEndpointV1() {
            return Collections.singletonMap(MESSAGE, "common from v1");
        }

        @Get( "/common")
        @Version("2")
        Map<String, String> commonEndpointV2() {
            return Collections.singletonMap(MESSAGE, "common from v2");
        }

        @Get( "/new")
        @Version("2")
        Map<String, String> newEndpointV2() {
            return Collections.singletonMap(MESSAGE, "new from v2");
        }

        @CrossOrigin(allowPrivateNetwork = false)
        @Get("/new-not-allowed-from-private")
        @Version("2")
        Map<String, String> newPrivateEndpointV2() {
            return Collections.singletonMap(MESSAGE, "new from v2");
        }

    }
}
