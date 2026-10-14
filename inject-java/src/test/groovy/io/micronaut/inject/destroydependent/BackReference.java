package io.micronaut.inject.destroydependent;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

/**
 * A dependent that references the bean it was created for.
 */
@Prototype
public class BackReference {

    Object owner;

    @PreDestroy
    void destroy() {
        owner = null;
    }
}
