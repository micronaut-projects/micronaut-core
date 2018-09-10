package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Factory
@Replaces(factory = CFactory.class)
public class CFactoryReplacement {

    @Singleton
    C getC() {
        return new C2();
    }
}
