package io.micronaut.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

import java.io.IOException;

public interface Serde<T> {

    void serialize(@NonNull Argument<? extends T> type,
                   @NonNull T value) throws IOException;

    @Nullable
    T deserialize(@NonNull Argument<? super T> type) throws IOException;
}
