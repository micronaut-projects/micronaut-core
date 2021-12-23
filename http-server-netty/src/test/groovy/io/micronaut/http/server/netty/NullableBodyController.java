package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Controller(consumes = { MediaType.ALL }, produces = { MediaType.ALL })
@Requires(property = "spec.name", value = "NullableBodySpec")
public class NullableBodyController {

    @Post(value = "/test2")
    boolean test2(@NotNull HttpRequest<@Nullable ByteBuffer<?>> request) {
        return request.getBody().isPresent();
    }

    @Post(value = "/test3")
    boolean test3(@NotNull HttpRequest<?> request,
                  @NotNull @Body Optional<ByteBuffer<?>> body) {
        return body.isPresent();
    }

    @Post(value = "/test4")
    boolean test4(@NotNull HttpRequest<?> request,
                  @Nullable @Body ByteBuffer<?> body) {
        return body != null;
    }

    @Post(value = "/test5")
    boolean test5(@NotNull HttpRequest<@Nullable ByteBuffer<?>> request,
                  @Nullable @Body ByteBuffer<?> body) {
        return body != null;
    }
}
