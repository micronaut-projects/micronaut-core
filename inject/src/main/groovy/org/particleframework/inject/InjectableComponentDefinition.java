package org.particleframework.inject;

import org.particleframework.context.Context;

/**
 * Created by graemerocher on 12/05/2017.
 */
public interface InjectableComponentDefinition<T> {
    /**
     * Inject the given bean with the context
     *
     * @param context The context
     * @param bean The bean
     * @return The injected bean
     */
    T inject(Context context, T bean);
}
