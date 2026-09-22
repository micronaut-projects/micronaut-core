package petstore.web;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.CompiledRouteMatcher;
import io.micronaut.web.router.RouteDeclaration;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes declared at compile time from the annotations of a made-up web framework, implemented by
 * handler functions: the annotation processor generates {@link PetResourceRoutes}, an enum of
 * route declarations with a generated URL parser, and {@link PetRoutes} binds a handler to each
 * constant, calling the resource bean.
 */
class CustomWebRoutesTest {
    private static final Map<String, Object> FORM = Map.of("name", "Bella", "age", "3");

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
    }

    @Test
    void theProcessorGeneratesAnEnumOfRouteDeclarations() {
        PetResourceRoutes[] routes = PetResourceRoutes.values();
        assertEquals(List.of("NAME", "OWNED", "PHOTO", "FILE", "ADD", "RENAME"),
            Arrays.stream(routes).map(Enum::name).toList());
        assertEquals(List.of("GET /pets/{id}", "GET /pets/{id}/owners/{owner}", "GET /pets/{id}/photo",
                "GET /pets/{id}/files/{+file}", "POST /pets", "POST /pets/{id}/rename"),
            Arrays.stream(routes).map(r -> r.httpMethod().name() + " " + r.uriTemplate()).toList());
    }

    @Test
    void theIndexKeysAreTheOnesTheRouterComputesAtRuntime() {
        for (PetResourceRoutes route : PetResourceRoutes.values()) {
            RouteDeclaration runtime = RouteDeclaration.of(route.httpMethod(), route.uriTemplate());
            assertEquals(runtime.requiredPathPrefix(), route.requiredPathPrefix(), route.name());
            assertEquals(runtime.rawLength(), route.rawLength(), route.name());
            assertEquals(runtime.pathVariableCount(), route.pathVariableCount(), route.name());
        }
    }

    @Test
    void theGeneratedParserMapsAPathToAConstant() {
        CompiledRouteMatcher matcher = PetResourceRoutes.NAME.matcher();
        assertEquals(2, matcher.maxVariables());
        assertEquals(List.of("NAME", "7"), parse(matcher, HttpMethod.GET, "/pets/7"));
        assertEquals(List.of("OWNED", "7", "abc"), parse(matcher, HttpMethod.GET, "/pets/7/owners/abc"));
        assertEquals(List.of("PHOTO", "7"), parse(matcher, HttpMethod.GET, "/pets/7/photo"));
        assertEquals(List.of("ADD"), parse(matcher, HttpMethod.POST, "/pets"));
        assertEquals(List.of("RENAME", "7"), parse(matcher, HttpMethod.POST, "/pets/7/rename"));
    }

    @Test
    void theGeneratedParserAnswersNothingForOtherRequests() {
        CompiledRouteMatcher matcher = PetResourceRoutes.NAME.matcher();
        assertEquals(List.of(), parse(matcher, HttpMethod.GET, "/pets"));
        assertEquals(List.of(), parse(matcher, HttpMethod.DELETE, "/pets/7"));
        assertEquals(List.of(), parse(matcher, HttpMethod.GET, "/pets/7/unknown"));
        assertEquals(List.of(), parse(matcher, HttpMethod.GET, "/cats/7"));
        // a template the parser does not compile is left to the router
        assertEquals(List.of(), parse(matcher, HttpMethod.GET, "/pets/7/files/a/b"));
    }

    @Test
    void theResourceIsAPlainBeanNotAController() {
        ApplicationContext context = server.getApplicationContext();
        assertTrue(context.getBeanDefinitions(Qualifiers.byStereotype(Controller.class)).isEmpty());
        assertTrue(context.getBeanDefinition(PetResource.class).getExecutableMethods().isEmpty());
    }

    @Test
    void boundDeclarationsAreLazyRoutesWithImplicitHead() {
        List<UriRouteInfo<?, ?>> petRoutes = server.getApplicationContext().getBean(Router.class).uriRoutes()
            .filter(route -> route.toString().contains("/pets"))
            .toList();
        assertEquals(Set.of("GET /pets/{id}", "HEAD /pets/{id}", "GET /pets/{id}/owners/{owner}",
                "HEAD /pets/{id}/owners/{owner}", "GET /pets/{id}/files/{+file}", "HEAD /pets/{id}/files/{+file}",
                "POST /pets", "POST /pets/{id}/rename"),
            petRoutes.stream().map(route -> route.getHttpMethodName() + " " + route.toString().split(" ")[1])
                .collect(Collectors.toSet()));
        assertTrue(petRoutes.stream().allMatch(route -> route.getClass().getSimpleName().equals("LazyUriRouteInfo")));
    }

    @Test
    void theRouterResolvesARequestWithTheGeneratedParser() throws Exception {
        Router router = server.getApplicationContext().getBean(Router.class);
        assertEquals("CapturedUriMatchInfo", matchedBy(router, HttpRequest.GET("/pets/1")));
        assertEquals("CapturedUriMatchInfo", matchedBy(router, HttpRequest.HEAD("/pets/1")));
        assertEquals("CapturedUriMatchInfo", matchedBy(router,
            HttpRequest.POST("/pets", FORM).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)));
        assertNotEquals("CapturedUriMatchInfo", matchedBy(router, HttpRequest.GET("/pets/1/files/a/b")));
    }

    @Test
    void handlersCallTheResourceWithTypedPathVariables() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("Rex", http.retrieve("/pets/1"));
        assertEquals("Rex of 3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e",
            http.retrieve("/pets/1/owners/3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e"));
        assertEquals(HttpStatus.OK, http.exchange(HttpRequest.HEAD("/pets/1")).getStatus());
        assertEquals("Rex: a/b", http.retrieve("/pets/1/files/a/b"));
    }

    @Test
    void handlersReadForms() {
        BlockingHttpClient http = client.toBlocking();
        String id = http.retrieve(HttpRequest.POST("/pets", FORM).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertEquals("Bella (3)", http.retrieve("/pets/" + id));

        var renamed = http.exchange(HttpRequest.POST("/pets/" + id + "/rename", Map.of("name", "Luna"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertEquals(HttpStatus.NO_CONTENT, renamed.getStatus());
        assertEquals("Luna", http.retrieve("/pets/" + id));
    }

    @Test
    void failuresAreTheOnesOfControllerRoutes() {
        BlockingHttpClient http = client.toBlocking();
        // a declared route without a handler is not a route
        assertEquals(HttpStatus.NOT_FOUND, status(() -> http.retrieve("/pets/1/photo")));
        // a path variable that does not convert
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> http.retrieve("/pets/rex")));
        // a missing form field
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> http.retrieve(HttpRequest.POST("/pets", Map.of("name", "Max"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))));
        // a method that is not routed
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, status(() -> http.exchange(HttpRequest.DELETE("/pets/1"))));
    }

    private static HttpStatus status(Runnable request) {
        return assertThrows(HttpClientResponseException.class, request::run).getStatus();
    }

    private static List<String> parse(CompiledRouteMatcher matcher, HttpMethod method, String path) {
        String[] variables = new String[matcher.maxVariables()];
        int ordinal = matcher.match(method, path, variables);
        if (ordinal < 0) {
            return List.of();
        }
        PetResourceRoutes route = PetResourceRoutes.values()[ordinal];
        List<String> result = new ArrayList<>();
        result.add(route.name());
        result.addAll(Arrays.asList(variables).subList(0, route.pathVariableCount()));
        return result;
    }

    private static String matchedBy(Router router, HttpRequest<?> request) throws ReflectiveOperationException {
        Object match = router.findClosest(request);
        Field matchInfo = match.getClass().getDeclaredField("matchInfo");
        matchInfo.setAccessible(true);
        return matchInfo.get(match).getClass().getSimpleName();
    }
}
