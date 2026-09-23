package io.micronaut.inject.beanimport.fixtures;

import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Inject;

public class PrivateInjectConstructorBean {

    @Inject
    @ReflectiveAccess
    private PrivateInjectConstructorBean() {
    }
}
