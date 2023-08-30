package io.micronaut.test.messageBodyWriter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.Writable;
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
@Singleton
public class FooMessageBodyWriter implements MessageBodyWriter {
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
        if (object instanceof Foo foo) {
            Writable writable = out -> {
                out.write("<!DOCTYPE html><html><head></head><body><h1>Foo: " + foo.name() + "</h1></body></html>");
            };
            try {
                writable.writeTo(outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("object is not Foo");
        }
    }
}
