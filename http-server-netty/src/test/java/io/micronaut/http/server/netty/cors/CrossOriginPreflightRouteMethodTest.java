package io.micronaut.http.server.netty.cors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.cors.CrossOrigin;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossOriginPreflightRouteMethodTest {
    private static final String SPEC_NAME = "CrossOriginPreflightRouteMethodTest";
    private static final String ORIGIN = "https://other.example";

    @Test
    void preflightIsNotApprovedByTheCrossOriginOfAnotherMethodRoute() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient blocking = client.toBlocking();

            HttpResponse<?> getPreflight = blocking.exchange(preflight(HttpMethod.GET));
            assertEquals(HttpStatus.OK, getPreflight.status());
            assertEquals(ORIGIN, getPreflight.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

            HttpResponse<?> postPreflight;
            try {
                postPreflight = blocking.exchange(preflight(HttpMethod.POST));
            } catch (HttpClientResponseException e) {
                postPreflight = e.getResponse();
            }
            assertNull(postPreflight.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
                "preflight for POST approved by the GET route's @CrossOrigin");

            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> blocking.exchange(
                HttpRequest.POST("/cors-route-method", "{}").contentType(MediaType.APPLICATION_JSON_TYPE).header(HttpHeaders.ORIGIN, ORIGIN)));
            assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        }
    }

    private static HttpRequest<?> preflight(HttpMethod method) {
        return HttpRequest.OPTIONS("/cors-route-method")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method.name())
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");
    }

    @Controller("/cors-route-method")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RouteMethodController {
        @Get
        @CrossOrigin(allowedOrigins = ORIGIN, allowCredentials = true)
        String read() {
            return "read";
        }

        @Post(consumes = MediaType.APPLICATION_JSON)
        String change(@Body String body) {
            return "changed";
        }
    }
}
