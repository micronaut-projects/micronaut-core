package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

@Prototype
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class TwiceReceivingPrototype {
    final Repo first;
    final Repo second;

    TwiceReceivingPrototype(Repo first, Repo second) {
        this.first = first;
        this.second = second;
    }
}
