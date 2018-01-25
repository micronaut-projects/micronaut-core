package org.particleframework.context.event;

import org.particleframework.context.BeanContext;

/**
 * An event fired once startup is complete
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class StartupEvent extends BeanContextEvent {
    /**
     * Constructs a prototypical Event.
     *
     * @param beanContext The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public StartupEvent(BeanContext beanContext) {
        super(beanContext);
    }
}
