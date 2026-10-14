package io.micronaut.inject.provider.exposedtypes;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MaybeConsumer {

    @Inject
    Maybe<String> present;

    @Inject
    @Nullable
    Maybe<String> absent;
}
