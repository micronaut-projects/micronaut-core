package io.micronaut.reflection;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A bean whose field carries a qualifier and no {@code @Inject}: the processors inject such a field, so a
 * reflective definition injects it too.
 */
@Singleton
public class DefQualified {

    @Named("express")
    Courier courier;

    public Courier getCourier() {
        return courier;
    }
}
