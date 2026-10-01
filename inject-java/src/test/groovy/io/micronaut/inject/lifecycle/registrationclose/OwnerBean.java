package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class OwnerBean {

    final DependentBean dependent;

    OwnerBean(DependentBean dependent) {
        this.dependent = dependent;
    }
}
