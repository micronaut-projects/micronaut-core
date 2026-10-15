package io.micronaut.aop.around.prototypetarget;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "PrototypeProxyTargetTest")
public class Collaborator {
    public String name() {
        return "collaborator";
    }
}
