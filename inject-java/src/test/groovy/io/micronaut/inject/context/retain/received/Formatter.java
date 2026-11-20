package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * A prototype bound to nothing.
 */
@Prototype
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class Formatter {
    public String format(String value) {
        return "[" + value + "]";
    }
}
