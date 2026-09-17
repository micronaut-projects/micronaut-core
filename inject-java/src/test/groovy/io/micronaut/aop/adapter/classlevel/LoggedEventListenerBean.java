package io.micronaut.aop.adapter.classlevel;

import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * A bean carrying class level around advice and declaring an adapted method.
 */
@Singleton
@Logged
@Requires(property = "spec.name", value = "ClassLevelAdviceOnAdaptedMethodSpec")
class LoggedEventListenerBean {

    final List<TheEvent> received = new ArrayList<>();

    @EventListener
    void onEvent(TheEvent event) {
        received.add(event);
    }
}
