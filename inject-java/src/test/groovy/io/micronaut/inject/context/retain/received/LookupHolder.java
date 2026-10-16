package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * A prototype that holds another, which holds a provider.
 */
@Prototype
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class LookupHolder {
    public final Lookup lookup;

    LookupHolder(Lookup lookup) {
        this.lookup = lookup;
    }
}
