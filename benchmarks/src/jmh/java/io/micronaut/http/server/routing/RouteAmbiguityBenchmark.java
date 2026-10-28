package io.micronaut.http.server.routing;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;

/**
 * Route matching when several routes match a request: the router narrows the candidates by
 * their templates, so the cost grows with their number. One candidate is the control, which
 * resolves no ambiguity.
 */
@State(Scope.Thread)
public class RouteAmbiguityBenchmark {

    @Param({"0", "1", "2", "8", "32"})
    int candidates = 8;

    @Param({"NONE", "ALL", "SOME"})
    String media = "NONE";

    Router router;
    HttpRequest<?> request;
    List<UriRouteMatch<Object, Object>> matches;

    @Setup
    public void setUp() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        // every route matches /items/1/details with the same template shape: all stay ambiguous
        for (int i = 0; i < candidates; i++) {
            var route = builder.POST("/items/{id" + i + "}/details");
            if (media.equals("ALL") || media.equals("SOME") && i % 2 == 0) {
                route.produces(MediaType.TEXT_PLAIN_TYPE).consumes(MediaType.TEXT_PLAIN_TYPE);
            } else {
                route.produces(MediaType.ALL_TYPE).consumesAll();
            }
            route.handle((r, p) -> HttpResponse.ok("ok"));
        }
        builder.close();
        router = new DefaultRouter(List.of(), List.of(() -> assembly));
        request = HttpRequest.POST("/items/1/details", "body").contentType(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.TEXT_PLAIN_TYPE);
        matches = List.copyOf(router.<Object, Object>find(HttpMethod.POST, request.getPath(), null).toList());
        if (candidates > 0 && match() == null) {
            throw new IllegalStateException("no match");
        }
    }

    @Benchmark
    public Object match() {
        List<UriRouteMatch<Object, Object>> matches = router.findAllClosest(request);
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Benchmark
    public Object narrow() {
        return DefaultRouter.resolveAmbiguity(request, matches);
    }
}
