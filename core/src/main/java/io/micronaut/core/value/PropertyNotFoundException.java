package io.micronaut.core.value;

/**
 * Thrown when a property cannot be resolved
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertyNotFoundException extends ValueException {

    public PropertyNotFoundException(String name, Class type) {
        super("No property found for name ["+name+"] and type ["+type.getName()+"]");
    }
}
