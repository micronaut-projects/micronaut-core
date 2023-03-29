package io.micronaut.http.server.netty.body;

import io.micronaut.http.server.netty.FormRouteCompleter;
import io.netty.buffer.ByteBufAllocator;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.function.Function;

public interface MultiObjectBody extends HttpBody {
    InputStream coerceToInputStream(ByteBufAllocator alloc);

    Publisher<?> asPublisher();

    MultiObjectBody mapNotNull(Function<Object, Object> transform);

    void handleForm(FormRouteCompleter formRouteCompleter);
}
