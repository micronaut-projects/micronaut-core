package io.micronaut.messaging.annotation;

import io.micronaut.runtime.EmbeddedApplication;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class SomeBean {

    @Inject EmbeddedApplication<?> embeddedApplication;
}
