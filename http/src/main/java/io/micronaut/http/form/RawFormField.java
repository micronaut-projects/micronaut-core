package io.micronaut.http.form;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.CloseableByteBody;

import java.io.Closeable;

public record RawFormField(@NonNull FormFieldMetadata metadata,
                           @NonNull CloseableByteBody byteBody) implements Closeable {
    @Override
    public void close() {
        byteBody.close();
    }
}
