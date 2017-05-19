package org.particleframework.context.condition;

import org.particleframework.context.annotation.Requires;

/**
 * Factory class for creating conditions
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class Conditions {

    /**
     * Create a condition for the given annotation
     *
     * @param requires The requires annotation
     * @return The condition
     */
    public static Condition forAnnotation(Requires requires) {
        Class[] beans = requires.beans();
        return null;
    }
}
