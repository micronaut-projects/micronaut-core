package io.micronaut.http.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.util.SimpleModel;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class MicronautHttpHandlerTest {

    private ApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.run();
    }

    @AfterEach
    void tearDown() {
        ctx.stop();
    }

    @Test
    public void testSimpleHttpRequest() {
        MicronautHttpHandler handler = ctx.getBean(MicronautHttpHandler.class);

        MutableHttpRequest<Object> request = HttpRequest.GET("/");

        HttpResponse<?> response = handler.handle(request);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getContentType()).hasValue(MediaType.TEXT_PLAIN_TYPE);
        assertThat(response.getBody().orElseThrow(RuntimeException::new))
            .isEqualTo("foo");
    }

    @Test
    public void testDefaults() {
        MicronautHttpHandler handler = ctx.getBean(MicronautHttpHandler.class);

        MutableHttpRequest<Object> request = HttpRequest.GET("/no-type");

        HttpResponse<?> response = handler.handle(request);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getContentType()).hasValue(MediaType.APPLICATION_JSON_TYPE);
        assertThat(response.getBody().orElseThrow(RuntimeException::new))
            .isEqualTo("foo");
    }

    @Test
    public void testJsonRequest() {
        MicronautHttpHandler handler = ctx.getBean(MicronautHttpHandler.class);

        MutableHttpRequest<Object> request = HttpRequest.GET("/json");

        HttpResponse<?> response = handler.handle(request);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getContentType()).hasValue(MediaType.APPLICATION_JSON_TYPE);
        assertThat(response.getBody().orElseThrow(RuntimeException::new))
            .isEqualTo(new SimpleModel("foo"));
    }
}
