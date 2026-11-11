package io.micronaut.aop.runtimetarget;

/**
 * What the beans of these tests run while a target is created, which holds a creation open at a chosen point.
 */
final class TargetProbe {

    static volatile Runnable onCreation = () -> { };

    private TargetProbe() {
    }
}
