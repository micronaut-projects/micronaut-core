package io.micronaut.http.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.server.util.SimpleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.test.StepVerifier;

public class MicronautAsyncHttpHandlerTest {

    ApplicationContext ctx;

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
        MicronautAsyncHttpHandler handler = ctx.getBean(MicronautAsyncHttpHandler.class);

        MutableHttpRequest<Object> request = HttpRequest.GET("/");

        Publisher<? extends HttpResponse<?>> result = handler.handleAsync(request);

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
                assertThat(response.getContentType()).hasValue(MediaType.TEXT_PLAIN_TYPE);
                assertThat(response.getBody().orElseThrow(RuntimeException::new))
                    .isEqualTo("foo");
            })
            .verifyComplete();
    }

    @Test
    public void testDefaults() {
        MicronautAsyncHttpHandler handler = ctx.getBean(MicronautAsyncHttpHandler.class);

        MutableHttpRequest<Object> request = HttpRequest.GET("/no-type");

        Publisher<? extends HttpResponse<?>> result = handler.handleAsync(request);

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
                assertThat(response.getContentType()).hasValue(MediaType.APPLICATION_JSON_TYPE);
                assertThat(response.getBody().orElseThrow(RuntimeException::new))
                    .isEqualTo("foo");
            })
            .verifyComplete();
    }

    @Test
    public void testJsonRequest() {
        MicronautAsyncHttpHandler handler = ctx.getBean(MicronautAsyncHttpHandler.class);

        MutableHttpRequest<Object> request = HttpRequest.GET("/json");

        Publisher<? extends HttpResponse<?>> result = handler.handleAsync(request);

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
                assertThat(response.getContentType()).hasValue(MediaType.APPLICATION_JSON_TYPE);
                assertThat(response.getBody().orElseThrow(RuntimeException::new))
                    .isEqualTo(new SimpleModel("foo"));
            })
            .verifyComplete();
    }

}
