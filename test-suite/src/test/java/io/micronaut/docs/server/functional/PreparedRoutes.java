package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import jakarta.inject.Singleton;
// end::imports[]

@Requires(property = "spec.name", value = "PreparedRoutesTest")
// tag::clazz[]
@Singleton
public class PreparedRoutes implements HttpDirectRoutes {

    private static final String VERSION = "\"2026.10\"";

    @Override
    public void routes(DirectRouteBuilder routes) {
        HttpResponse<?> current = routes.prepare(HttpResponse.ok("release 2026.10") // <1>
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header(HttpHeaders.ETAG, VERSION));
        HttpResponse<?> notModified = routes.prepare(HttpResponse.notModified()
            .header(HttpHeaders.ETAG, VERSION));
        routes.GET("/release").respond(direct ->
            VERSION.equals(direct.request().header(HttpHeaders.IF_NONE_MATCH)) ? notModified : current); // <2>
    }
}
// end::clazz[]
