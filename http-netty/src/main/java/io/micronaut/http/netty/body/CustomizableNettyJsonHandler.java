package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.JsonFeatures;

/**
 * {@link io.micronaut.http.body.MessageBodyHandler} that is customizable with {@link JsonFeatures}.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public interface CustomizableNettyJsonHandler {
    @NonNull
    CustomizableNettyJsonHandler customize(@NonNull JsonFeatures jsonFeatures);
}
