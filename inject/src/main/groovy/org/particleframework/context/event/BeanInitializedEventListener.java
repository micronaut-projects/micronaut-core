package org.particleframework.context.event;

import javax.inject.Provider;
import java.util.EventListener;

/**
 * <p>Allows hooking into bean instantiation at the point prior to when {@link javax.annotation.PostConstruct} initialization hooks have been called and in this case of bean {@link javax.inject.Provider} instances the {@link Provider#get()} method has not yet been invoked</p>
 *
 * <p>This allows (for example) customization of bean properties prior to any initialization logic or factory logic.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanInitializedEventListener<T> extends EventListener {

    /**
     * <p>Fired when a bean is instantiated but the {@link javax.annotation.PostConstruct} initialization hooks have not yet been called
     * and in this case of bean {@link javax.inject.Provider} instances the {@link Provider#get()} method has not yet been invoked</p>
     *
     * @param event The bean initializing event
     * @return The bean or a replacement bean of the same type
     */
    T onInitialized(BeanInitializingEvent<T> event);
}
