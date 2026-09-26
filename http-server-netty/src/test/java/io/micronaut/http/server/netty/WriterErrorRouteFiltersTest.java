package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The response replacing one whose body failed to be written is filtered by the response filters
 * of the route: of a handler route and of its groups, like the {@code @ResponseFilter} methods of
 * the server filters that apply to a controller.
 */
public class WriterErrorRouteFiltersTest {
    private static final String SPEC = "WriterErrorRouteFiltersTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        ctx.close();
    }

    private static java.net.http.HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
            java.net.http.HttpRequest.newBuilder(server.getURI().resolve(path)).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void theResponseFiltersOfAControllerFilterTheErrorResponse() throws Exception {
        var response = get("/wrf-controller/failing");
        assertEquals(418, response.statusCode());
        assertEquals("handled: failing", response.body());
        assertEquals("true", response.headers().firstValue("X-Server-Filter").orElse(null));
    }

    @Test
    void theResponseFiltersOfAHandlerRouteAndItsGroupFilterTheErrorResponse() throws Exception {
        var response = get("/wrf-handler/failing");
        assertEquals(418, response.statusCode());
        assertEquals("handled: failing", response.body());
        assertEquals("true", response.headers().firstValue("X-Server-Filter").orElse(null));
        assertEquals("true", response.headers().firstValue("X-Group-After").orElse(null));
        assertEquals("true", response.headers().firstValue("X-Route-After").orElse(null));
    }

    @Test
    void theResponseFiltersOfAHandlerRouteFilterItsResponse() throws Exception {
        var response = get("/wrf-handler/ok");
        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
        assertEquals("true", response.headers().firstValue("X-Server-Filter").orElse(null));
        assertEquals("true", response.headers().firstValue("X-Group-After").orElse(null));
        assertEquals("true", response.headers().firstValue("X-Route-After").orElse(null));
    }

    record FailingBody(String message) {
    }

    static final class FailingWriterException extends RuntimeException {
        FailingWriterException(String message) {
            super(message);
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/wrf-controller")
    static class FailingController {
        @Get(value = "/failing", produces = MediaType.TEXT_PLAIN)
        FailingBody failing() {
            return new FailingBody("failing");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/wrf-handler", group -> {
                group.after((request, response) -> response.header("X-Group-After", "true"));
                group.GET("/failing", (request, pathVariables) ->
                    HttpResponse.ok(new FailingBody("failing")).contentType(MediaType.TEXT_PLAIN_TYPE)
                ).after((request, response) -> response.header("X-Route-After", "true"));
                group.GET("/ok", (request, pathVariables) ->
                    HttpResponse.ok("ok").contentType(MediaType.TEXT_PLAIN_TYPE)
                ).after((request, response) -> response.header("X-Route-After", "true"));
            });
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerFilter({"/wrf-controller/**", "/wrf-handler/**"})
    static class ServerFilters {
        @ResponseFilter
        void response(MutableHttpResponse<?> response) {
            response.header("X-Server-Filter", "true");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class FailingWriter implements MessageBodyWriter<FailingBody> {
        @Override
        public void writeTo(Argument<FailingBody> type, MediaType mediaType, FailingBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new FailingWriterException(object.message());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class FailingWriterExceptionHandler implements ExceptionHandler<FailingWriterException, HttpResponse<String>> {
        @Override
        public HttpResponse<String> handle(HttpRequest request, FailingWriterException exception) {
            return HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT).body("handled: " + exception.getMessage());
        }
    }
}
