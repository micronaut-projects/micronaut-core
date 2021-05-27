package io.micronaut.messaging.annotation;

import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class SomeBean {

    @Inject EmbeddedApplication<?> embeddedApplication;
}
