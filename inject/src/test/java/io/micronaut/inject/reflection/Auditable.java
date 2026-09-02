package io.micronaut.inject.reflection;

import java.util.List;

/**
 * A second, parallel declaration of the method {@link Reservable} declares: a type implementing both
 * inherits the method from two branches that know nothing of each other.
 */
public interface Auditable {

    @Tag("auditable")
    List<@Tag("auditable-item") String> reserve(@Tag("auditable-param") String name);
}
