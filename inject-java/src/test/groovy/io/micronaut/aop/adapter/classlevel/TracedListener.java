package io.micronaut.aop.adapter.classlevel;

/**
 * A SAM interface to adapt that declares its own advice.
 */
@Traced
@FunctionalInterface
public interface TracedListener {

    void handle(TheEvent event);
}
