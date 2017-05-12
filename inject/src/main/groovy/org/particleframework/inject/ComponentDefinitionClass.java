package org.particleframework.inject;

import org.particleframework.core.annotation.Internal;

/**
 * <p>A component definition class provides a reference to a {@link ComponentDefinition} thus
 * allowing for soft loading of component definitions for purposes of type inspection.</p>
 *
 * <p>The class can also decided whether to abort loading the definition by returning null</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface ComponentDefinitionClass<T> {
    /**
     * @return The underlying component type
     */
    Class<T> getComponentType();

    /**
     * Loads the component definition
     *
     * @return The loaded component definition or null if it shouldn't be loaded
     */
    ComponentDefinition<T> load();
}
