package org.particleframework.inject;

import org.particleframework.context.Context;

/**
 * A bean definition that is injectable
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InjectableComponentDefinition<T> extends ComponentDefinition<T> {
    /**
     * Inject the given bean with the context
     *
     * @param context The context
     * @param bean The bean
     * @return The injected bean
     */
    T inject(Context context, T bean);
}
