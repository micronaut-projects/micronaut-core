package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.JsonFeatures;

@Internal
public interface CustomizableNettyJsonHandler {
    @NonNull
    CustomizableNettyJsonHandler customize(@NonNull JsonFeatures jsonFeatures);
}
