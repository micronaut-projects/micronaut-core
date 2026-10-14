package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;

/**
 * Listed by {@link IndexMarker} because it is indexed by it, without implementing it.
 */
@Singleton
@Indexed(IndexMarker.class)
@Requires(property = "spec.name", value = "BeanWatchTest")
public class MarkedOnly {
}
