package io.micronaut.inject.provider.injectionpointowner;

import io.micronaut.context.annotation.EachBean;
import jakarta.inject.Inject;

/**
 * A consumer created once per {@link Seed}, from a qualified delegate of its definition.
 */
@EachBean(Seed.class)
public class EachSeedConsumer {

    final Seed seed;

    final Owned<Integer> constructed;

    @Inject
    Owned<String> injected;

    public EachSeedConsumer(Seed seed, Owned<Integer> constructed) {
        this.seed = seed;
        this.constructed = constructed;
    }
}
