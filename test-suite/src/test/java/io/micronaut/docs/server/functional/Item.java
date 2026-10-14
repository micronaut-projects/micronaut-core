package io.micronaut.docs.server.functional;

import io.micronaut.core.annotation.Introspected;

/**
 * An item of the documentation examples.
 *
 * @param id   The id
 * @param name The name
 */
@Introspected
public record Item(long id, String name) {
}
