package org.particleframework.context.event;

import org.particleframework.context.BeanContext;

/**
 * An event fired on shutdown
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
