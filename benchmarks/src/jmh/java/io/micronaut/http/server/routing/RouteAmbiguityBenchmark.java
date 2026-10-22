package io.micronaut.http.server.routing;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
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

    @Param({"1", "2", "8", "32"})
    int candidates = 8;

    Router router;
    HttpRequest<?> request;

    @Setup
    public void setUp() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        // every route matches /items/1/details with the same template shape: all stay ambiguous
        for (int i = 0; i < candidates; i++) {
            builder.GET("/items/{id" + i + "}/details", (r, p) -> HttpResponse.ok("ok"));
        }
        builder.close();
        router = new DefaultRouter(List.of(), List.of(() -> assembly));
        request = HttpRequest.GET("/items/1/details");
        if (match() == null) {
            throw new IllegalStateException("no match");
        }
    }

    @Benchmark
    public Object match() {
        List<UriRouteMatch<Object, Object>> matches = router.findAllClosest(request);
        return matches.isEmpty() ? null : matches.get(0);
    }
}
