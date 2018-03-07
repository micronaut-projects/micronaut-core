package io.micronaut.context.event;

import io.micronaut.context.BeanContext;

/**
 * An event fired prior to starting shutdown sequence
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ShutdownEvent extends BeanContextEvent {
    /**
     * Constructs a prototypical Event.
     *
     * @param beanContext The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public ShutdownEvent(BeanContext beanContext) {
        super(beanContext);
    }
}
