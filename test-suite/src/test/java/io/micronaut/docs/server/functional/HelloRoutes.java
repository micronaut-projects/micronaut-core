package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
// end::imports[]

@Requires(property = "spec.name", value = "HelloRoutesTest")
// tag::clazz[]
@Singleton
public class HelloRoutes implements HttpRoutes {
    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.GET("/hello/{name}", (request, pathVariables) ->
            HttpResponse.ok("Hello " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
    }
}
// end::clazz[]
