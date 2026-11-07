package io.micronaut.aop.runtimetarget;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class TargetDependency {

    public TargetDependency() {
        TargetProbe.onCreation.run();
    }
}
