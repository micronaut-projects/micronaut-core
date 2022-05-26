package io.micronaut.inject.generics;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "TypeArgumentsSpec")
@Singleton
class MyBean {

    MyBean(Provider<String> provider) {}
}
