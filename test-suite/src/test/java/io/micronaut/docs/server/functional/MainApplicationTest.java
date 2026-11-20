package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainApplicationTest {

    private static final String[] ARGS = {"-micronaut.server.port=-1", "-spec.name=MainApplicationTest"};

    @Test
    void routesInTheMainMethod() {
        try (ApplicationContext context = MainApplication.start(ARGS);
             HttpClient client = client(context)) {
            assertEquals("Hello World", client.toBlocking().retrieve(HttpRequest.GET("/hello/World")));
        }
    }

    @Test
    void routesThatUseBeans() {
        try (ApplicationContext context = MainApplication.startWithBeans(ARGS);
             HttpClient client = client(context)) {
            Item saved = client.toBlocking().retrieve(HttpRequest.POST("/items", new Item(0, "Pen")), Item.class);
            assertEquals("Pen", client.toBlocking().retrieve(HttpRequest.GET("/items/" + saved.id()), Item.class).name());
        }
    }

    @Test
    void chosenServerWithItsConfiguration() {
        try (ApplicationContext context = MainApplication.startOnNetty(ARGS);
             HttpClient client = client(context)) {
            assertEquals("pong", client.toBlocking().retrieve(HttpRequest.GET("/ping")));
            assertEquals(16384, context.getBean(NettyHttpServerConfiguration.class).getMaxHeaderSize());
        }
    }

    private static HttpClient client(ApplicationContext context) {
        return context.createBean(HttpClient.class, context.getBean(EmbeddedServer.class).getURL());
    }
}
