package io.micronaut.inject.provider.exposedtypes;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class HandleConsumer {

    @Inject
    Lookup<String> lookup;

    final Source<Integer> source;

    @Inject
    Handle<Long> handle;

    public HandleConsumer(Source<Integer> source) {
        this.source = source;
    }
}
