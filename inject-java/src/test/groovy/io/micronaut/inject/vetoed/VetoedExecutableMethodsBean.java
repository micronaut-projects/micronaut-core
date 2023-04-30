package io.micronaut.inject.vetoed;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Vetoed;
import jakarta.inject.Singleton;

@Singleton
@Executable
public class VetoedExecutableMethodsBean {

    void foo() {
    }

    @Vetoed
    void bar() {
    }

    void abc() {
    }

    @Executable
    @Vetoed
    void xyz() {
    }

}
