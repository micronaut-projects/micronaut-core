package io.micronaut.http.server.stack;

import com.sun.management.ThreadMXBean;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Bytes the server threads allocate for each message echoed by a functional WebSocket route and by a
 * {@code @ServerWebSocket}, over a real connection.
 */
public class WebSocketAllocProfile {
    private static final ThreadMXBean MX = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Singleton
    @Requires(property = "spec.name", value = "WebSocketAllocProfile")
    public static class FnRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/fws").webSocket(ws -> ws
                .onMessage(String.class, (session, message) -> session.sendAsync("echo " + message)));
        }
    }

    @ServerWebSocket("/cws")
    @Requires(property = "spec.name", value = "WebSocketAllocProfile")
    public static class Ctl {
        @OnMessage
        public CompletionStage<String> onMessage(String message, WebSocketSession session) {
            return session.sendAsync("echo " + message).thenApply(s -> s);
        }
    }

    static long serverBytes() {
        long total = 0;
        for (long id : MX.getAllThreadIds()) {
            java.lang.management.ThreadInfo info = MX.getThreadInfo(id);
            if (info != null && info.getThreadName().startsWith("default-eventLoopGroup")) {
                total += MX.getThreadAllocatedBytes(id);
            }
        }
        return total;
    }

    static double run(String url, int warm, int iters) throws Exception {
        Semaphore replies = new Semaphore(0);
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                replies.release();
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }
        }).get(10, TimeUnit.SECONDS);
        ws.request(1);
        for (int i = 0; i < warm; i++) {
            ws.sendText("hello", true).get();
            replies.acquire();
        }
        Thread.sleep(200);
        long before = serverBytes();
        for (int i = 0; i < iters; i++) {
            ws.sendText("hello", true).get();
            replies.acquire();
        }
        Thread.sleep(200);
        double perMessage = (serverBytes() - before) / (double) iters;
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
        return perMessage;
    }

    public static void main(String[] args) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "WebSocketAllocProfile", "micronaut.python.enabled", false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            String base = "ws://localhost:" + server.getPort();
            int warm = Integer.getInteger("profile.warmup", 20_000);
            int iters = Integer.getInteger("profile.iters", 20_000);
            if (Boolean.getBoolean("profile.threads")) {
                run(base + "/fws", 100, 100);
                for (long id : MX.getAllThreadIds()) {
                    java.lang.management.ThreadInfo info = MX.getThreadInfo(id);
                    if (info != null) {
                        System.out.println("THREAD " + info.getThreadName() + " " + MX.getThreadAllocatedBytes(id));
                    }
                }
            }
            for (int round = 0; round < 3; round++) {
                double f = run(base + "/fws", warm, iters);
                double c = run(base + "/cws", warm, iters);
                System.out.printf("websocket echo   function %8.0f B/msg   controller %8.0f B/msg   delta %+7.0f (%+.0f%%)%n", f, c, f - c, (f - c) / c * 100);
            }
        }
        System.exit(0);
    }
}
