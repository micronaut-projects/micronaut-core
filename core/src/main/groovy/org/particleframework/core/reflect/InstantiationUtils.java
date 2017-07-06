package org.particleframework.core.reflect;

import org.particleframework.core.reflect.exception.InstantiationException;

/**
 * Utility methods for instantiating objects
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class InstantiationUtils {

    public static <T> T instantiate(Class<T> type) {
        try {
            return type.newInstance();
        } catch (Throwable e) {
            throw new InstantiationException("Could not instantiate type ["+type.getName()+"]: " + e.getMessage(),e);
        }
    }
}
