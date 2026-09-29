package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Argument binding, streamed responses and exception handlers give the same results for every
 * request, now that parts of their work are resolved once per route.
 */
public class RouteBindingPlanTest {
    private static final String SPEC = "RouteBindingPlanTest";
    private static final String EXECUTOR = "route-binding-plan";

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

    private static java.net.http.HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(server.getURI().resolve(path)).GET();
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static java.net.http.HttpResponse<String> postJson(String path, String body, String... headers) throws IOException, InterruptedException {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(server.getURI().resolve(path))
            .header("Content-Type", MediaType.APPLICATION_JSON)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void requestBeanWithConstructor() throws Exception {
        for (int i = 0; i < 3; i++) {
            var response = get("/binding-plan/record/42?page=3&filter=x", "X-Tenant", "acme");
            assertEquals(200, response.statusCode());
            assertEquals("42 acme 3 Optional[x]", response.body());
        }
        var response = get("/binding-plan/record/42?page=3", "X-Tenant", "acme");
        assertEquals("42 acme 3 Optional.empty", response.body());
    }

    @Test
    void requestBeanWithMissingRequiredValue() throws Exception {
        var response = get("/binding-plan/record/42?page=3");
        assertEquals(400, response.statusCode());
        // the same once the plan exists
        response = get("/binding-plan/record/42?page=3");
        assertEquals(400, response.statusCode());
    }

    @Test
    void requestBeanWithConversionError() throws Exception {
        var response = get("/binding-plan/record/42?page=abc", "X-Tenant", "acme");
        assertEquals(400, response.statusCode());
    }

    @Test
    void requestBeanWithSetters() throws Exception {
        var response = get("/binding-plan/setters/7?sort=name", "X-Tenant", "acme");
        assertEquals(200, response.statusCode());
        assertEquals("7 name acme", response.body());
        response = get("/binding-plan/setters/7", "X-Tenant", "acme");
        assertEquals("7 null acme", response.body());
    }

    @Test
    void nestedRequestBean() throws Exception {
        var response = get("/binding-plan/nested?id=5&page=2");
        assertEquals(200, response.statusCode());
        assertEquals("5 2 GET", response.body());
    }

    @Test
    void nullableRequestBeanWithNothingBound() throws Exception {
        var response = get("/binding-plan/nullable");
        assertEquals(200, response.statusCode());
        assertEquals("null", response.body());
        response = get("/binding-plan/nullable?q=x");
        assertEquals("x", response.body());
    }

    @Test
    void unmatchedArgumentPrefersThePathVariable() throws Exception {
        var response = postJson("/binding-plan/unmatched/path?name=query", "{\"name\":\"body\"}", "X-Attribute", "attribute");
        assertEquals(200, response.statusCode());
        assertEquals("path", response.body());
    }

    @Test
    void unmatchedArgumentPrefersTheQueryValue() throws Exception {
        for (int i = 0; i < 2; i++) {
            var response = get("/binding-plan/unmatched-get?name=query", "X-Attribute", "attribute");
            assertEquals(200, response.statusCode());
            assertEquals("query", response.body());
        }
        var response = get("/binding-plan/unmatched-get", "X-Attribute", "attribute");
        assertEquals("attribute", response.body());
    }

    @Test
    void unmatchedArgumentIgnoresTheQueryWhenTheMethodPermitsABody() throws Exception {
        var response = postJson("/binding-plan/unmatched?name=query", "{\"name\":\"body\"}", "X-Attribute", "attribute");
        assertEquals(200, response.statusCode());
        assertEquals("attribute", response.body());
    }

    @Test
    void unmatchedArgumentPrefersTheAttributeOverTheBody() throws Exception {
        var response = postJson("/binding-plan/unmatched", "{\"name\":\"body\"}", "X-Attribute", "attribute");
        assertEquals(200, response.statusCode());
        assertEquals("attribute", response.body());
    }

    @Test
    void unmatchedArgumentFallsBackToTheBody() throws Exception {
        for (int i = 0; i < 2; i++) {
            var response = postJson("/binding-plan/unmatched", "{\"name\":\"body\"}");
            assertEquals(200, response.statusCode());
            assertEquals("body", response.body());
        }
    }

    @Test
    void unmatchedArgumentMissing() throws Exception {
        var response = postJson("/binding-plan/unmatched", "{}");
        assertEquals(400, response.statusCode());
        response = get("/binding-plan/unmatched-optional");
        assertEquals(200, response.statusCode());
        assertEquals("none", response.body());
    }

    @Test
    void unmatchedArgumentConversionError() throws Exception {
        var response = get("/binding-plan/unmatched-int?count=abc");
        assertEquals(400, response.statusCode());
        response = get("/binding-plan/unmatched-int?count=12");
        assertEquals("12", response.body());
    }

    @Test
    void pathVariableWinsOverTheBinderOfTheArgument() throws Exception {
        for (int i = 0; i < 2; i++) {
            var response = get("/binding-plan/wins/path?tenant=query", "X-Tenant", "header");
            assertEquals(200, response.statusCode());
            assertEquals("path header", response.body());
        }
    }

    @Test
    void optionalPathVariable() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertEquals("5", get("/binding-plan/optional/5").body());
            assertEquals("query", get("/binding-plan/optional?id=query").body());
            assertEquals("none", get("/binding-plan/optional").body());
        }
    }

    @Test
    void pathVariableAndBody() throws Exception {
        for (int i = 0; i < 2; i++) {
            var response = postJson("/binding-plan/body/7", "{\"name\":\"body\"}");
            assertEquals(200, response.statusCode());
            assertEquals("7 body", response.body());
        }
    }

    @Test
    void everyBinderGetsTheLocaleOfTheRequest() throws Exception {
        var response = get("/binding-plan/locale", "Accept-Language", "de-DE");
        assertEquals(200, response.statusCode());
        assertEquals("de-DE de-DE", response.body());
        response = get("/binding-plan/locale");
        String defaultLocale = Locale.getDefault().toLanguageTag();
        assertEquals(defaultLocale + " " + defaultLocale, response.body());
    }

    @Test
    void streamedBodyIsPublishedOnTheRouteExecutor() throws Exception {
        for (int i = 0; i < 2; i++) {
            var response = get("/binding-plan/stream");
            assertEquals(200, response.statusCode());
            assertEquals("[" + EXECUTOR + "]", response.body());
        }
    }

    @Test
    void exceptionHandlerHandlesEveryException() throws Exception {
        PlanExceptionHandler handler = ctx.getBean(PlanExceptionHandler.class);
        int before = handler.calls.get();
        for (int i = 0; i < 3; i++) {
            var response = get("/binding-plan/handled");
            assertEquals(HttpStatus.CONFLICT.getCode(), response.statusCode());
            assertEquals("handled", response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith(MediaType.TEXT_PLAIN));
        }
        assertEquals(before + 3, handler.calls.get());
    }

    @Controller("/binding-plan")
    @Requires(property = "spec.name", value = SPEC)
    @Produces(MediaType.TEXT_PLAIN)
    static class PlanController {

        @Get("/record/{id}")
        String record(@RequestBean RecordBean bean) {
            return bean.id() + " " + bean.tenant() + " " + bean.page() + " " + bean.filter();
        }

        @Get("/setters/{id}")
        String setters(@RequestBean SettersBean bean) {
            return bean.getId() + " " + bean.getSort() + " " + bean.getTenant();
        }

        @Get("/nested")
        String nested(@RequestBean OuterBean bean) {
            return bean.inner().id() + " " + bean.inner().page() + " " + bean.request().getMethodName();
        }

        @Get("/nullable")
        String nullable(@Nullable @RequestBean NullableBean bean) {
            return bean == null ? "null" : bean.q();
        }

        @Post("/unmatched/{name}")
        String unmatchedWithPath(String name) {
            return name;
        }

        @Post("/unmatched")
        String unmatched(String name) {
            return name;
        }

        @Get("/wins/{tenant}")
        String wins(@QueryValue("tenant") String tenant, @Header("X-Tenant") String header) {
            return tenant + " " + header;
        }

        @Get("/optional{/id}")
        String optional(Optional<String> id) {
            return id.orElse("none");
        }

        @Post("/body/{id}")
        String body(String id, @Body Map<String, String> body) {
            return id + " " + body.get("name");
        }

        @Get("/locale")
        String locale(LocaleTag first, LocaleTag second) {
            return first.tag() + " " + second.tag();
        }

        @Get("/unmatched-get")
        String unmatchedGet(String name) {
            return name;
        }

        @Get("/unmatched-optional")
        String unmatchedOptional(Optional<String> name) {
            return name.orElse("none");
        }

        @Get("/unmatched-int")
        String unmatchedInt(int count) {
            return String.valueOf(count);
        }

        @ExecuteOn(EXECUTOR)
        @Get(value = "/stream", produces = MediaType.APPLICATION_JSON)
        Flux<String> stream() {
            return Flux.defer(() -> Flux.just(Thread.currentThread().getName()));
        }

        @Get("/handled")
        String handled() {
            throw new PlanException();
        }
    }

    @Introspected
    record RecordBean(@PathVariable Long id, @Header("X-Tenant") String tenant, @QueryValue int page,
                      @QueryValue Optional<String> filter) {
    }

    @Introspected
    record InnerBean(@QueryValue Long id, @QueryValue int page) {
    }

    @Introspected
    record OuterBean(@RequestBean InnerBean inner, HttpRequest<?> request) {
    }

    @Introspected
    record NullableBean(@Nullable @QueryValue String q) {
    }

    @Introspected
    static class SettersBean {
        @PathVariable
        private Long id;
        @Nullable
        @QueryValue
        private String sort;
        @Header("X-Tenant")
        private String tenant;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public @Nullable String getSort() {
            return sort;
        }

        public void setSort(@Nullable String sort) {
            this.sort = sort;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

    @ServerFilter("/binding-plan/**")
    @Requires(property = "spec.name", value = SPEC)
    static class AttributeFilter {
        @RequestFilter
        void filter(HttpRequest<?> request) {
            String attribute = request.getHeaders().get("X-Attribute");
            if (attribute != null) {
                request.setAttribute("name", attribute);
            }
        }
    }

    record LocaleTag(String tag) {
    }

    /**
     * Binds the locale of the conversion context the binder gets.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class LocaleTagBinder implements TypedRequestArgumentBinder<LocaleTag> {
        @Override
        public Argument<LocaleTag> argumentType() {
            return Argument.of(LocaleTag.class);
        }

        @Override
        public BindingResult<LocaleTag> bind(ArgumentConversionContext<LocaleTag> context, HttpRequest<?> source) {
            LocaleTag tag = new LocaleTag(context.getLocale().toLanguageTag());
            return () -> Optional.of(tag);
        }
    }

    static final class PlanException extends RuntimeException {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class PlanExceptionHandler implements ExceptionHandler<PlanException, HttpResponse<String>> {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public HttpResponse<String> handle(HttpRequest request, PlanException exception) {
            calls.incrementAndGet();
            return HttpResponse.<String>status(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN_TYPE).body("handled");
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class ExecutorFactory {
        @Singleton
        @Named(EXECUTOR)
        @Bean(preDestroy = "shutdown")
        ExecutorService executor() {
            return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, EXECUTOR));
        }
    }
}
