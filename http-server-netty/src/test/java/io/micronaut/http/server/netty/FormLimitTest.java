package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class FormLimitTest {
    @Test
    public void test() {

        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "FormLimitTest"
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class)) {
            // TODO
        }
    }

    @Requires(property = "spec.name", value = "FormLimitTest")
    @Controller("/form-limit")
    static final class MyController {
        @Post("/buffered")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Publisher<HttpResponse<?>> buffered(@Body Publisher<CompletedAttribute> attributes) {
            return Flux.from(attributes)
                .collectList()
                .map(list -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (CompletedAttribute attr : list) {
                        values.put(attr.getName(), attr.toReadBuffer().toString(StandardCharsets.UTF_8));
                    }
                    return HttpResponse.ok(values);
                });
        }
    }
}
