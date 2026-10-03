package io.micronaut.aop.adapter.classlevel;

import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * A bean with advice on the adapted method rather than on the class. The advice belongs to the method, so it
 * has to keep running when the generated adapter delegates to it.
 */
@Singleton
@Requires(property = "spec.name", value = "MethodLevelAdviceOnAdaptedMethodSpec")
class MethodAdvisedEventListenerBean {

    final List<TheEvent> received = new ArrayList<>();

    @EventListener
    @Logged
    void onEvent(TheEvent event) {
        received.add(event);
    }
}
