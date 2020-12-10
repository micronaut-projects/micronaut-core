package org.atinject.tck.auto.events;

import io.micronaut.core.annotation.Indexed;

import java.util.EventListener;

@Indexed(value = EventHandlerMultipleArguments.class)
public interface EventHandlerMultipleArguments<M extends Metadata, E extends Event> extends EventListener {

    void onEvent(M metadata, E event);

}
