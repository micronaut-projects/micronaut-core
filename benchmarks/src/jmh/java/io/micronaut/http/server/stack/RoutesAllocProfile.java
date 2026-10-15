package io.micronaut.http.server.stack;

import com.sun.management.ThreadMXBean;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpClientCodec;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Allocation profile of functional routes against equivalent controllers: runs each request through
 * the real server pipeline on an {@link EmbeddedChannel} and reports the bytes allocated per request.
 * Run with {@code -Dprofile.only=NAME} to restrict to one case, {@code -Dprofile.iters=N} to change
 * the measured iterations.
 */
public class RoutesAllocProfile {
    private static final ThreadMXBean MX = (ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

    @Introspected
    public record Item(int id, String name) {
    }

    public record Tenant(String id) implements PropagatedContextElement {
    }

    @Singleton
    @Requires(property = "spec.name", value = "RoutesAllocProfile")
    public static class FnRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/f/hello").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pv) -> HttpResponse.ok("Hello").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/f/var/{id}").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pv) -> HttpResponse.ok("id " + pv.getInt("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/f/json").produces(MediaType.TEXT_PLAIN_TYPE).consumes(MediaType.APPLICATION_JSON_TYPE).body(Item.class)
                .handle((request, pv, item) -> HttpResponse.ok("got " + item.name()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/f/json-async").produces(MediaType.TEXT_PLAIN_TYPE).consumes(MediaType.APPLICATION_JSON_TYPE).body()
                .handleAsync((request, pv, body) -> body.body(Item.class)
                    .thenApply(item -> HttpResponse.ok("got " + item.name()).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.POST("/f/text-async").produces(MediaType.TEXT_PLAIN_TYPE).consumes(MediaType.TEXT_PLAIN_TYPE).body()
                .handleAsync((request, pv, body) -> body.text(1024)
                    .thenApply(t -> HttpResponse.ok("len " + t.length()).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.POST("/f/form").produces(MediaType.TEXT_PLAIN_TYPE).form().handle((request, pv, form) ->
                HttpResponse.ok("Welcome " + form.getString("name") + ", " + form.getInt("age", 18)).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/f/multipart").produces(MediaType.TEXT_PLAIN_TYPE).consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body()
                .handleAsync((request, pv, body) -> body.form().thenApply(form ->
                    HttpResponse.ok(form.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.GET("/f/sse").sse((request, pv, events) -> {
                for (int i = 1; i <= 3; i++) {
                    events.send(Event.of("e" + i).id(String.valueOf(i)));
                }
            });
            routes.GET("/f/filtered").produces(MediaType.TEXT_PLAIN_TYPE)
                .before(request -> request.getHeaders().add("X-In", "1")).and()
                .after((request, response) -> response.header("X-Out", "1")).and()
                .handle((request, pv) -> HttpResponse.ok("filtered").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/f/ctx").produces(MediaType.TEXT_PLAIN_TYPE)
                .beforeReplacing((request, ctx) -> {
                    ctx.add(new Tenant("t1"));
                    return null;
                }).and()
                .handle((request, pv) -> HttpResponse.ok("tenant " + PropagatedContext.get().get(Tenant.class).id())
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/f/async-filtered")
                .beforeAsync(request -> CompletableFuture.completedFuture(null)).and()
                .afterAsync((request, response) -> CompletableFuture.completedFuture(null)).and()
                .handle((request, pv) -> HttpResponse.ok("afiltered").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.path("/f/group", g -> {
                g.beforeReplacing((request, ctx) -> {
                    ctx.add(new Tenant("g"));
                    return null;
                });
                g.after((request, response) -> response.header("X-G", "1"));
                g.GET("/x", (request, pv) -> HttpResponse.ok("g " + PropagatedContext.get().get(Tenant.class).id())
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
            });
        }
    }

    @Controller("/c")
    @Requires(property = "spec.name", value = "RoutesAllocProfile")
    public static class Ctl {
        @Get("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        String hello() {
            return "Hello";
        }

        @Get("/var/{id}")
        @Produces(MediaType.TEXT_PLAIN)
        String var(int id) {
            return "id " + id;
        }

        @Post("/json")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.TEXT_PLAIN)
        String json(@Body Item item) {
            return "got " + item.name();
        }

        @Post("/json-async")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.TEXT_PLAIN)
        CompletableFuture<String> jsonAsync(@Body CompletableFuture<Item> item) {
            return item.thenApply(i -> "got " + i.name());
        }

        @Post("/text-async")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        CompletableFuture<String> textAsync(@Body CompletableFuture<String> t) {
            return t.thenApply(s -> "len " + s.length());
        }

        @Post("/form")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Produces(MediaType.TEXT_PLAIN)
        String form(String name, int age) {
            return "Welcome " + name + ", " + age;
        }

        @Post("/multipart")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Produces(MediaType.TEXT_PLAIN)
        String multipart(String name) {
            return name;
        }

        @Get("/sse")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> sse() {
            return Flux.range(1, 3).map(i -> Event.of("e" + i).id(String.valueOf(i)));
        }

        @Get("/filtered")
        @Produces(MediaType.TEXT_PLAIN)
        String filtered() {
            return "filtered";
        }

        @Get("/ctx")
        @Produces(MediaType.TEXT_PLAIN)
        String ctx() {
            return "tenant " + PropagatedContext.get().get(Tenant.class).id();
        }
    }

    @ServerFilter("/c/filtered")
    @Requires(property = "spec.name", value = "RoutesAllocProfile")
    public static class CtlFilter {
        @RequestFilter
        void in(io.micronaut.http.MutableHttpRequest<?> request) {
            request.getHeaders().add("X-In", "1");
        }

        @ResponseFilter
        void out(MutableHttpResponse<?> response) {
            response.header("X-Out", "1");
        }
    }

    @ServerFilter("/c/ctx")
    @Requires(property = "spec.name", value = "RoutesAllocProfile")
    public static class CtlCtxFilter {
        @RequestFilter
        void in(MutablePropagatedContext ctx) {
            ctx.add(new Tenant("t1"));
        }
    }

    record Case(String name, String baseline, String expect, List<Object> request) {
    }

    static List<Object> get(String uri) {
        DefaultFullHttpRequest r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        r.headers().add(HttpHeaderNames.HOST, "localhost");
        return List.of(r);
    }

    static List<Object> post(String uri, String type, String body) {
        DefaultFullHttpRequest r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri,
            Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));
        r.headers().add(HttpHeaderNames.HOST, "localhost");
        r.headers().add(HttpHeaderNames.CONTENT_TYPE, type);
        r.headers().add(HttpHeaderNames.CONTENT_LENGTH, r.content().readableBytes());
        return List.of(r);
    }

    static final String BOUNDARY = "xBOUNDARYx";

    static String multipartBody() {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nfred\r\n--" + BOUNDARY + "--\r\n";
    }

    static List<Case> cases() {
        List<Case> cs = new ArrayList<>();
        cs.add(new Case("hello", "/c/hello", "Hello", get("/f/hello")));
        cs.add(new Case("pathvar", "/c/var/7", "id 7", get("/f/var/7")));
        cs.add(new Case("json", "/c/json", "got fred", post("/f/json", "application/json", "{\"id\":1,\"name\":\"fred\"}")));
        cs.add(new Case("json-async", "/c/json-async", "got fred", post("/f/json-async", "application/json", "{\"id\":1,\"name\":\"fred\"}")));
        cs.add(new Case("text-async", "/c/text-async", "len 5", post("/f/text-async", "text/plain", "hello")));
        cs.add(new Case("form", "/c/form", "Welcome fred, 42", post("/f/form", "application/x-www-form-urlencoded", "name=fred&age=42")));
        cs.add(new Case("multipart", "/c/multipart", "fred", post("/f/multipart", "multipart/form-data; boundary=" + BOUNDARY, multipartBody())));
        cs.add(new Case("sse", "/c/sse", "data: e3", get("/f/sse")));
        cs.add(new Case("filtered", "/c/filtered", "filtered", get("/f/filtered")));
        cs.add(new Case("ctx", "/c/ctx", "tenant t1", get("/f/ctx")));
        cs.add(new Case("async-filtered", "/c/filtered", "afiltered", get("/f/async-filtered")));
        cs.add(new Case("group", "/c/ctx", "g g", get("/f/group/x")));
        return cs;
    }

    static List<Object> retarget(Case c) {
        // The controller request: same method, body and headers, baseline URI
        DefaultFullHttpRequest src = (DefaultFullHttpRequest) c.request().get(0);
        DefaultFullHttpRequest r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, src.method(), c.baseline(), src.content().retainedDuplicate());
        r.headers().add(src.headers());
        return List.of(r);
    }

    static final class Runner implements AutoCloseable {
        final ApplicationContext ctx;
        final EmbeddedChannel channel;

        Runner() {
            ctx = ApplicationContext.run(Map.of(
                "spec.name", "RoutesAllocProfile",
                "micronaut.python.enabled", false,
                "micronaut.server.date-header", false));
            channel = ((NettyHttpServer) ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
        }

        String exchange(List<Object> req) {
            for (Object o : req) {
                channel.writeInbound(((io.netty.handler.codec.http.FullHttpRequest) o).retainedDuplicate());
            }
            channel.runPendingTasks();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (channel.outboundMessages().isEmpty()) {
                if (System.nanoTime() > deadline) {
                    return null;
                }
                Thread.onSpinWait();
                channel.runPendingTasks();
            }
            channel.runPendingTasks();
            StringBuilder sb = new StringBuilder();
            Object m;
            while ((m = channel.readOutbound()) != null) {
                if (m instanceof ByteBuf b) {
                    sb.append(b.toString(StandardCharsets.UTF_8));
                    b.release();
                } else {
                    sb.append(m);
                    io.netty.util.ReferenceCountUtil.release(m);
                }
            }
            return sb.toString();
        }

        /** Discards the output without decoding to keep the measurement to the server work. */
        void exchangeQuiet(List<Object> req) {
            for (Object o : req) {
                channel.writeInbound(((io.netty.handler.codec.http.FullHttpRequest) o).retainedDuplicate());
            }
            channel.runPendingTasks();
            while (channel.outboundMessages().isEmpty()) {
                Thread.onSpinWait();
                channel.runPendingTasks();
            }
            channel.runPendingTasks();
            Object m;
            while ((m = channel.readOutbound()) != null) {
                io.netty.util.ReferenceCountUtil.release(m);
            }
        }

        @Override
        public void close() {
            ctx.close();
        }
    }

    static double bytesPerRequest(Runner r, List<Object> req, int iters) {
        long id = Thread.currentThread().threadId();
        long before = MX.getThreadAllocatedBytes(id);
        for (int i = 0; i < iters; i++) {
            r.exchangeQuiet(req);
        }
        return (MX.getThreadAllocatedBytes(id) - before) / (double) iters;
    }

    public static void main(String[] args) throws Exception {
        int iters = Integer.getInteger("profile.iters", 20_000);
        int warm = Integer.getInteger("profile.warmup", 30_000);
        String only = System.getProperty("profile.only");
        Map<String, double[]> results = new LinkedHashMap<>();
        try (Runner r = new Runner()) {
            for (Case c : cases()) {
                if (only != null && !only.equals(c.name())) {
                    continue;
                }
                List<Object> ctl = retarget(c);
                String fo = r.exchange(c.request());
                String co = r.exchange(ctl);
                boolean fok = fo != null && fo.contains(c.expect());
                boolean cok = co != null && co.contains(c.expect());
                if (!fok || !cok) {
                    System.out.printf("%-16s SKIP correctness fn=%s ctl=%s%n  fn: %s%n  ctl: %s%n", c.name(), fok, cok,
                        fo == null ? null : fo.replace("\r\n", "|"), co == null ? null : co.replace("\r\n", "|"));
                    continue;
                }
                String side = System.getProperty("profile.side", "both");
                if (!side.equals("both")) {
                    List<Object> req = side.equals("fn") ? c.request() : ctl;
                    for (int i = 0; i < warm + iters * 3; i++) {
                        r.exchangeQuiet(req);
                    }
                    continue;
                }
                for (int i = 0; i < warm; i++) {
                    r.exchangeQuiet(c.request());
                    r.exchangeQuiet(ctl);
                }
                double f = 0;
                double k = 0;
                // interleave rounds to cancel drift
                for (int round = 0; round < 3; round++) {
                    f += bytesPerRequest(r, c.request(), iters);
                    k += bytesPerRequest(r, ctl, iters);
                }
                results.put(c.name(), new double[] {f / 3, k / 3});
                System.out.printf("%-16s function %8.0f B/req   controller %8.0f B/req   delta %+7.0f (%+.0f%%)%n",
                    c.name(), f / 3, k / 3, (f - k) / 3, (f - k) / k * 100);
            }
        }
        System.exit(0);
    }
}
