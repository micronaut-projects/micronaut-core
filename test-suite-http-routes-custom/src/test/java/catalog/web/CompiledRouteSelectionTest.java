package catalog.web;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A declared route that a generated URL parser answers is selected like any other route: the most
 * specific route, the preferred media type and an explicit {@code HEAD} route win, whether the
 * competing route is a controller route or the route of another parser. Only a compiled route no
 * other route competes with skips the selection.
 */
class CompiledRouteSelectionTest {
    static final String SPEC_NAME = "CompiledRouteSelectionTest";

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
    }

    @Test
    void aLiteralControllerRouteWinsOverADeclaredVariable() {
        HttpResponse<String> response = get(HttpRequest.GET("/items/stats").accept(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("stats", response.header("X-Route"));
        assertEquals("controller stats", response.body());
    }

    @Test
    void theDeclaredRouteAnswersItsOwnPaths() {
        HttpResponse<String> response = get(HttpRequest.GET("/items/5").accept(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("declared", response.header("X-Route"));
        assertEquals("declared item 5", response.body());
    }

    @Test
    void thePreferredMediaTypeSelectsTheRoute() {
        HttpResponse<String> response = get(HttpRequest.GET("/items/5").accept(MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_JSON_TYPE));
        assertEquals("text", response.header("X-Route"));
        assertEquals("controller text 5", response.body());
    }

    @Test
    void anExplicitHeadRouteWinsOverTheImplicitHeadOfADeclaredRoute() {
        HttpResponse<String> response = get(HttpRequest.HEAD("/items/5"));
        assertEquals("explicit-head", response.header("X-Route"));
    }

    @Test
    void aLiteralDeclaredRouteWinsOverADeclaredVariable() {
        HttpResponse<String> response = get(HttpRequest.GET("/items/special").accept(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("special", response.header("X-Route"));
        assertEquals("declared special", response.body());
    }

    @Test
    void onlyARouteWithoutCompetitorsSkipsTheSelection() throws ReflectiveOperationException {
        Router router = server.getApplicationContext().getBean(Router.class);
        assertEquals("CapturedUriMatchInfo", matchedBy(router, HttpRequest.GET("/docs/1")));
        assertEquals("CapturedUriMatchInfo", matchedBy(router, HttpRequest.HEAD("/docs/1")));
        assertNotEquals("CapturedUriMatchInfo", matchedBy(router, HttpRequest.GET("/items/5").accept(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals("declared doc 1", client.toBlocking().retrieve("/docs/1"));
    }

    private static HttpResponse<String> get(HttpRequest<?> request) {
        BlockingHttpClient http = client.toBlocking();
        return http.exchange(request, String.class);
    }

    private static String matchedBy(Router router, HttpRequest<?> request) throws ReflectiveOperationException {
        Object match = router.findClosest(request);
        Field matchInfo = match.getClass().getDeclaredField("matchInfo");
        matchInfo.setAccessible(true);
        return matchInfo.get(match).getClass().getSimpleName();
    }
}
