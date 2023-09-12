package io.micronaut.test.messageBodyWriter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.Writable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;

@Requires(property = "spec.name", value = "MessageBodyWriterIsWritableTest")
@Produces(MediaType.TEXT_HTML)
@Order(Ordered.LOWEST_PRECEDENCE)
@Singleton
public class FooThrowMessageBodyWriter implements MessageBodyWriter {
    @Override
    public boolean isWriteable(@NonNull Argument type, @Nullable MediaType mediaType) {
        return type.getType().equals(Foo.class);
    }

    @Override
    public void writeTo(@NonNull Argument type,
                        @NonNull MediaType mediaType,
                        Object object,
                        @NonNull MutableHeaders outgoingHeaders,
                        @NonNull OutputStream outputStream) throws CodecException {
        throw new RuntimeException("Throw Throw Throw");
    }
}
