
package io.micronaut.inject.provider.injectionpointowner;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;

@Prototype
public class OwningConsumer {

    final Owned<Integer> constructed;

    @Inject
    Owned<String> injected;

    public OwningConsumer(Owned<Integer> constructed) {
        this.constructed = constructed;
    }
}
