package org.particleframework.context.event;

import java.util.EventListener;

/**
 * <p>An event listener that is triggered each time a bean is created.</p>
 *
 * <p>Allows customization of the created beans.</p>
 *
 * @see BeanCreatedEvent
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanCreatedEventListener<T> extends EventListener {

    /**
     * Fired when a bean is created and all {@link javax.annotation.PostConstruct} initialization hooks have been called
     *
     * @param event The bean created event
     * @return The bean or a replacement bean of the same type
     */
    T onCreated(BeanCreatedEvent<T> event);
}
