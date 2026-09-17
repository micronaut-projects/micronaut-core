package io.micronaut.aop.adapter.classlevel;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Class level advice on the declaring bean, and advice on the adapted interface.
 */
@Singleton
@Logged
@Requires(property = "spec.name", value = "InterfaceAdviceOnAdapterSpec")
class LoggedTracedListenerBean {

    final List<TheEvent> received = new ArrayList<>();

    @TracedListenerMethod
    void onEvent(TheEvent event) {
        received.add(event);
    }
}
