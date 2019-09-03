package io.micronaut.test.replaces;

import io.micronaut.context.annotation.Factory;
import org.testcontainers.shaded.javax.inject.Singleton;

@Factory
public class InterfaceCFactory {

    @Singleton
    InterfaceC interfaceC() {
        return () -> "real";
    }
}
