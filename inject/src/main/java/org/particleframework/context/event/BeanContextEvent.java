package org.particleframework.context.event;

import org.particleframework.context.BeanContext;

import java.util.EventObject;

/**
 * A BeanContextEvent is an event fired from the {@link BeanContext} instance
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class BeanContextEvent extends EventObject {
    /**
     * Constructs a prototypical Event.
     *
     * @param beanContext The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public BeanContextEvent(BeanContext beanContext) {
        super(beanContext);
    }

    @Override
    public BeanContext getSource() {
        return (BeanContext) super.getSource();
    }
}
